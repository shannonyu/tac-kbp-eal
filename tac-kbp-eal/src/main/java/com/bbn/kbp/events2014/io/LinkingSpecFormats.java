package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.transform;

public final class LinkingSpecFormats {

  private LinkingSpecFormats() {
    throw new UnsupportedOperationException();
  }

  public static LinkingStore openOrCreateLinkingStore(File directory) {
    directory.mkdirs();
    return new DirectoryLinkingStore(directory);
  }

  public static LinkingStore openLinkingStore(final File directory) throws FileNotFoundException {
    if (!directory.isDirectory()) {
      throw new FileNotFoundException("Not a directory: " + directory);
    }
    return new DirectoryLinkingStore(directory);
  }

  /* package-private */
  static Optional<ResponseLinking> readFromLinkingStoreTransformingIDs(final LinkingStore store,
      Symbol docID, Set<Response> responses,
      Optional<ImmutableMap<String, String>> foreignIDToLocal)
      throws IOException {
    if (store instanceof DirectoryLinkingStore) {
      return ((DirectoryLinkingStore) store).read(docID, responses, foreignIDToLocal);
    } else {
      throw new RuntimeException(
          "Expected a linking store of type DirectoryLinkingStore but got " + store.getClass());
    }
  }

  /**
   * {@link com.bbn.kbp.events2014.io.LinkingStore} implementations which uses a directory with one
   * file per doc ID.  Each file contains a {@link com.bbn.kbp.events2014.ResponseSet} on each line
   * in the form of response {@link com.bbn.kbp.events2014.Response#uniqueIdentifier()}s separated
   * by spaces. Finally, there is a line at the bottom in the same format except prefixed by
   * "INCOMPLETE " which lists responses which have neither been linked nor marked as singletons.
   * The incomplete set is for the use of the annotation tool only.
   *
   * Both the incomplete set and the response set lines are sorted within themselves by response ID.
   * The response set lines are sorted among themselves lexicographically by response ID.
   *
   * Blank lines are allowed, as are comment lines (the first character on the line must be #).
   */
  private static final class DirectoryLinkingStore implements LinkingStore {

    private final File directory;
    private boolean closed = false;

    private DirectoryLinkingStore(File directory) {
      checkArgument(directory.isDirectory(), "Specified directory %s for "
          + "linking store is not a directory", directory);
      this.directory = checkNotNull(directory);
    }

    @Override
    public ImmutableSet<Symbol> docIDs() throws IOException {
      checkNotClosed();

      return FluentIterable.from(Arrays.asList(directory.listFiles()))
          .transform(FileUtils.ToName)
          .transform(Symbol.FromString)
          .toSet();
    }

    @Override
    public Optional<ResponseLinking> read(AnswerKey answerKey) throws IOException {
      return read(answerKey.docId(), answerKey.allResponses());
    }

    @Override
    public Optional<ResponseLinking> read(ArgumentOutput argumentOutput) throws IOException {
      return read(argumentOutput.docId(), argumentOutput.responses());
    }

    public Optional<ResponseLinking> read(Symbol docID, Set<Response> responses)
        throws IOException {
      return read(docID, responses, Optional.<ImmutableMap<String, String>>absent());
    }

    private static final Splitter ON_TABS = Splitter.on('\t').trimResults().omitEmptyStrings();

    private static final ImmutableSet<String> ACCEPTABLE_SUFFIXES = ImmutableSet.of("linking");

    private Optional<ResponseLinking> read(Symbol docID, Set<Response> responses,
        Optional<ImmutableMap<String, String>> foreignIDToLocal)
        throws IOException {
      checkNotClosed();

      final File f;
      try {
        f = AssessmentSpecFormats.bareOrWithSuffix(directory, docID.toString(),
            ACCEPTABLE_SUFFIXES);
      } catch (FileNotFoundException ioe) {
        return Optional.absent();
      }

      final Map<String, Response> responsesByUID = Maps.uniqueIndex(responses,
          Response.uniqueIdFunction());

      final ImmutableSet.Builder<ResponseSet> ret = ImmutableSet.builder();
      ImmutableSet<Response> incompleteResponses = ImmutableSet.of();

      int lineNo = 0;
      for (final String line : Files.asCharSource(f, UTF_8).readLines()) {
        ++lineNo;
        // empty lines are allowed, and comments on lines
        // beginning with '#'
        if (line.isEmpty() || line.charAt(0) == '#') {
          continue;
        }
        boolean incomplete;
        final Iterable<String> parts;
        if (line.startsWith("INCOMPLETE")) {
          incomplete = true;
          parts = Iterables.skip(ON_TABS.split(line), 1);
        } else {
          incomplete = false;
          parts = ON_TABS.split(line);
        }

        final ImmutableSet.Builder<Response> responseSet = ImmutableSet.builder();
        for (String idString : parts) {
          final String newID;
          // for translating a foreign id
          if (foreignIDToLocal.isPresent()) {
            if (!foreignIDToLocal.get().containsKey(idString)) {
              throw new RuntimeException(
                  "Could not find a new id for " + idString + " have id mappings "
                      + foreignIDToLocal.get());
            }
            newID = foreignIDToLocal.get().get(idString);
          } else {
            newID = idString;
          }
          final Response responseForIDString = responsesByUID.get(newID);
          if (responseForIDString == null) {
            throw new IOException(
                "While reading " + docID + ", on line " + lineNo + ", ID " + newID
                    + "(original ID) " + idString
                    + " cannot be resolved using provided response store. Known"
                    + "response IDs are " + responsesByUID.keySet()
                    + "transformed response ids are " + foreignIDToLocal);
          }
          responseSet.add(responseForIDString);
        }
        if (incomplete) {
          incompleteResponses = responseSet.build();
        } else {
          ret.add(ResponseSet.from(responseSet.build()));
        }
      }

      return Optional.of(ResponseLinking.from(docID, ret.build(), incompleteResponses));
    }

    private static final Joiner TAB_JOINER = Joiner.on("\t");

    @Override
    public void write(ResponseLinking responseLinking) throws IOException {
      checkNotClosed();

      final List<String> lines = Lists.newArrayList();
      for (final ResponseSet responseSet : responseLinking.responseSets()) {
        lines.add(TAB_JOINER.join(
            transform(responseSet.asSet(), Response.uniqueIdFunction())));
      }

      // incompletes last
      lines.add("INCOMPLETE\t" + TAB_JOINER.join(
          transform(responseLinking.incompleteResponses(), Response.uniqueIdFunction())));

      final File f = new File(directory, responseLinking.docID().toString());
      Files.asCharSink(f, Charsets.UTF_8).writeLines(lines, "\n");
    }

    @Override
    public void close() throws IOException {
      closed = true;
    }

    private void checkNotClosed() throws IOException {
      if (closed) {
        throw new IOException("Cannot perform I/O operations on a closed output store");
      }
    }

    @Override
    public String toString() {
      return "DirectoryLinkingStore(" + directory + ")";
    }
  }
}