package com.bbn.kbp;

import com.google.common.base.Function;

import java.util.Set;

/**
 * An assertion about some reified object, or node, in the knowledge-base. At the very least, an
 * assertion is a "subject predicate object" triple, but it may contain other fields. The subject is
 * always a node, but the predicate and object can be multiple different things depending on the
 * type of triple. Possible additional fields for certain assertions include "provenance" and
 * "confidence".
 */
public interface Assertion {

  Node subject();

  Set<Node> allNodes();

  enum SubjectFunction implements Function<Assertion, Node> {
    INSTANCE;

    @Override
    public Node apply(final Assertion input) {
      return input.subject();
    }
  }
}
