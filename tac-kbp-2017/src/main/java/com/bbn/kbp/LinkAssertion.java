package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import org.immutables.value.Value;

/**
 * An assertion linking some entity to an entity of an external knowledge-base. The format of this
 * assertion is "[EntityNodeID] link [QuotedString ExternalKBID:ExternalNodeID]".
 */
@TextGroupImmutable
@Value.Immutable
public abstract class LinkAssertion implements Assertion {

  @Override
  public abstract EntityNode subject();

  public abstract Symbol externalKB();

  public abstract Symbol externalNodeID();

  public static LinkAssertion of(final EntityNode subject, final  Symbol externalKB,
      final Symbol externalNodeID) {

    return ImmutableLinkAssertion.builder()
        .subject(subject)
        .externalKB(externalKB)
        .externalNodeID(externalNodeID)
        .build();
  }

}
