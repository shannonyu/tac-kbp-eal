package com.bbn.kbp.events2014;

import com.bbn.bue.common.collections.CollectionUtils;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.concat;

/**
 * A linking of {@link Response}s into a document-level event.
 *
 * The IDs on a response linking have no canonical form; they would change due to various filtering
 * steps (e.g. filtering {@link Response}'s in quote regions) and not be easily mappable to original
 * participant input.
 */
@Value.Immutable(prehash = true)
// old code, we don't care if it uses deprecated stuff
@SuppressWarnings("deprecation")
@com.bbn.bue.common.TextGroupPublicImmutable
@Functional
abstract class _ResponseLinking {

  public abstract Symbol docID();

  public abstract ImmutableSet<ResponseSet> responseSets();

  public abstract ImmutableSet<Response> incompleteResponses();

  public abstract Optional<ImmutableBiMap<String, ResponseSet>> responseSetIds();

  @Value.Derived
  public ImmutableMultimap<Response, ResponseSet> responsesToContainingResponseSets() {
    final ImmutableMultimap.Builder<Response, ResponseSet> ret = ImmutableMultimap.builder();
    for (final ResponseSet responseSet : responseSets()) {
      for (final Response response : responseSet) {
        ret.put(response, responseSet);
      }
    }
    return ret.build();
  }

  @Value.Check
  protected void checkValidity() {
    // no incomplete response may appear in any response set
    final ImmutableSet<Response> allResponsesInSets = ImmutableSet.copyOf(
        concat(responseSets()));
    for (final Response incompleteResponse : incompleteResponses()) {
      checkArgument(!allResponsesInSets.contains(incompleteResponse),
          "A response may not be both completed and incomplete");
    }
    if (responseSetIds().isPresent()) {
      for (final String id : responseSetIds().get().keySet()) {
        checkArgument(!id.contains("-"), "Event frame IDs may not contain -s");
        checkArgument(!id.contains("\t"), "Event frame IDs may not contain tabs");
      }
      // we can have an empty output file, verify that all the responseSets have an id
      checkArgument(responseSets().size() == responseSetIds().get().size(),
          "Response set IDs are missing");
      checkArgument(responseSetIds().get().keySet().size() == responseSets().size(),
          "All response set IDs must be unique");
      CollectionUtils.assertSameElementsOrIllegalArgument(responseSets(),
          responseSetIds().get().values(), "Response sets did not match IDs",
          "Response sets in list", "Response sets in ID map");
    }
  }

  public final ImmutableSet<Response> allResponses() {
    return ImmutableSet.copyOf(concat(concat(responseSets()), incompleteResponses()));
  }

  public DocEventFrameReference asEventFrameReference(ResponseSet rs) {
    checkArgument(responseSets().contains(rs), "Response set not found in linking");
    checkArgument(responseSetIds().isPresent(), "Cannot create event frame references without "
        + "response IDs");
    return DocEventFrameReference.of(docID(), responseSetIds().get().inverse().get(rs));
  }

  @Override
  public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("docID", docID())
        .add("responseSets", responseSets())
        .add("incomplete", incompleteResponses()).toString();
  }

  public ResponseLinking copyWithFilteredResponses(final Predicate<Response> toKeepCondition) {
    final Set<Response> newIncompletes = Sets.filter(incompleteResponses(), toKeepCondition);
    final ImmutableSet.Builder<ResponseSet> newResponseSetsB = ImmutableSet.builder();
    final ImmutableBiMap.Builder<String, ResponseSet> responseSetsIdB = ImmutableBiMap.builder();
    // to account for ResponseSets merging due to lost annotation
    final Set<ResponseSet> alreadyAdded = Sets.newHashSet();
    for (final ResponseSet responseSet : responseSets()) {
      final ImmutableSet<Response> okResponses = FluentIterable.from(responseSet.asSet())
          .filter(toKeepCondition).toSet();
      if (!okResponses.isEmpty()) {
        final ResponseSet newResponseSet = ResponseSet.from(okResponses);
        if(alreadyAdded.contains(newResponseSet)) {
          continue;
        }
        alreadyAdded.add(newResponseSet);
        newResponseSetsB.add(newResponseSet);
        if (responseSetIds().isPresent()) {
          responseSetsIdB.put(responseSetIds().get().inverse().get(responseSet), newResponseSet);
        }
      }
    }

    final ImmutableSet<ResponseSet> newResponseSets = newResponseSetsB.build();
    final ResponseLinking.Builder ret = ResponseLinking.builder().docID(docID())
        .responseSets(newResponseSets).incompleteResponses(newIncompletes);
    if (responseSetIds().isPresent()) {
      ret.responseSetIds(responseSetsIdB.build());
    }
    return ret.build();
  }
}
