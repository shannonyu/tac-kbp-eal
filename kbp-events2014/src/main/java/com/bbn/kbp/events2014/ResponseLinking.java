package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.in;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class ResponseLinking {
    private final Symbol docId;
    private final ImmutableSet<ResponseSet> responseSets;
    private final ImmutableSet<Response> incompleteResponses;

    private ResponseLinking(Symbol docId, Iterable<ResponseSet> responseSets,
                            Iterable<Response> incompleteResponses)
    {
        this.docId = checkNotNull(docId);
        this.responseSets = ImmutableSortedSet.copyOf(responseSets);
        this.incompleteResponses = ImmutableSortedSet.copyOf(
                Response.byUniqueIdOrdering(), incompleteResponses);
        checkValidity();
    }

    public static ResponseLinking from(Symbol docId, Iterable<ResponseSet> responseSets,
                                       Iterable<Response> incompleteResponses)
    {
        return new ResponseLinking(docId, responseSets, incompleteResponses);
    }

    public static ResponseLinking createEmpty(Symbol docId) {
        return from(docId, ImmutableList.<ResponseSet>of(), ImmutableList.<Response>of());
    }

    /**
     * Creates a response linking with containing all responses from the given
     * answer key which are correct (up to justifications) and have CASes
     * with coref annotations.  These will all be marked as 'incomplete'.
     */
    public static ResponseLinking createUnlinkedFor(AnswerKey answerKey) {
        final Predicate<Response> CASHasBeenCoreffed =
                compose(in(answerKey.corefAnnotation().annotatedCASes()),
                        Response.CASFunction());

        final ImmutableSet<Response> linkingCandidates = FluentIterable
                .from(answerKey.annotatedResponses())
                .filter(AssessedResponse.IsCorrectUpToInexactJustifications)
                .transform(AssessedResponse.Response)
                .filter(CASHasBeenCoreffed)
                .toSet();

        return from(answerKey.docId(), ImmutableSet.<ResponseSet>of(),
                        Iterables.transform(answerKey.annotatedResponses(), AssessedResponse.Response));
    }

    public Symbol docID() {
        return docId;
    }

    public ImmutableSet<ResponseSet> responseSets() {
        return responseSets;
    }

    public ImmutableSet<Response> incompleteResponses() {
        return incompleteResponses;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(docId, responseSets, incompleteResponses);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final ResponseLinking other = (ResponseLinking) obj;
        return Objects.equal(this.docId, other.docId)
                && Objects.equal(this.responseSets, other.responseSets)
                && Objects.equal(this.incompleteResponses, incompleteResponses);
    }

    private void checkValidity() {
        // no incomplete response may appear in any response set
        final ImmutableSet<Response> allResponsesInSets = ImmutableSet.copyOf(
                Iterables.concat(responseSets));
        for (final Response incompleteResponse : incompleteResponses) {
            checkArgument(!allResponsesInSets.contains(incompleteResponse),
                    "A response may not be both completed and incomplete");
        }
    }
}
