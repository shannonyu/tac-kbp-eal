package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.kbp.events2014.EntityNormalizer;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Deduplicates a system output by keeping only a single representative for each (docid, type,
 * role, CAS, realis) tuple.  The selection criterion is the same as for the scorer - prefer
 * the higher, and if there is a tie, prefer the 'larger' ID (which is computed from a stable
 * hash code).
 */
public final class KeepBestJustificationOnly implements Function<SystemOutput, SystemOutput> {
    private static final Logger log = LoggerFactory.getLogger(KeepBestJustificationOnly.class);
    // this 'normalizer' is just a dummy which does no normalization
    private static final EntityNormalizer identityNormalizer = EntityNormalizer.createDummy();

    @Override
    public SystemOutput apply(SystemOutput input) {
        checkNotNull(input);

        // group response by TypeRoleFillerRealis tuples
        final Multimap<TypeRoleFillerRealis, Response> groupedResponses =
           Multimaps.index(input.responses(),
                TypeRoleFillerRealis.extractFromSystemResponse(identityNormalizer));

        final ImmutableSet.Builder<Scored<Response>> filteredResults = ImmutableSet.builder();

        for (final Collection<Response> group : groupedResponses.asMap().values()) {
            // we know by construction known of those groups is empty, so the .get() is safe
            filteredResults.add(input.score(input.selectFromMultipleSystemResponses(group).get()));
        }

        final ImmutableSet<Scored<Response>> filteredResponses = filteredResults.build();

        log.info("For document {}, after keeping only selected justifications, went from {} to {} responses",
                input.docId(), input.size(), filteredResponses.size());

        return SystemOutput.from(input.docId(), filteredResponses);
    }

    private KeepBestJustificationOnly() {

    }

    public static KeepBestJustificationOnly create() {
        return new KeepBestJustificationOnly();
    }
}