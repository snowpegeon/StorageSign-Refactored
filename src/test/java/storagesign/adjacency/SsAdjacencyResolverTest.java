package storagesign.adjacency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class SsAdjacencyResolverTest {

    @Test
    void findFirst_usesRuleFirstMatchWithoutAllocatingMatchLists() {
        SsAdjacencyRule rule1 = mock(SsAdjacencyRule.class);
        SsAdjacencyRule rule2 = mock(SsAdjacencyRule.class);
        SsAdjacencyResolver resolver = new SsAdjacencyResolver(List.of(rule1, rule2));

        SsAdjacencyQuery query = new SsAdjacencyQuery(mock(Block.class), null, SsAdjacencyPurpose.INVENTORY_TRANSFER);
        SsAdjacencyMatch expected = mock(SsAdjacencyMatch.class);

        when(rule1.findFirstMatch(query)).thenReturn(Optional.empty());
        when(rule2.findFirstMatch(query)).thenReturn(Optional.of(expected));

        Optional<SsAdjacencyMatch> resolved = resolver.findFirst(query);

        assertSame(expected, resolved.orElseThrow());
        verify(rule1).findFirstMatch(query);
        verify(rule2).findFirstMatch(query);
        verify(rule1, never()).findMatches(any());
        verify(rule2, never()).findMatches(any());
    }

    @Test
    void findAll_deduplicatesBySignBlockPreservingFirstMatchOrder() {
        SsAdjacencyRule rule1 = mock(SsAdjacencyRule.class);
        SsAdjacencyRule rule2 = mock(SsAdjacencyRule.class);
        SsAdjacencyResolver resolver = new SsAdjacencyResolver(List.of(rule1, rule2));

        SsAdjacencyQuery query = new SsAdjacencyQuery(mock(Block.class), null, SsAdjacencyPurpose.ATTACHED_SIGN_DROP);

        Block blockA = mock(Block.class);
        Block blockB = mock(Block.class);

        SsAdjacencyMatch matchA = mock(SsAdjacencyMatch.class);
        SsAdjacencyMatch matchB = mock(SsAdjacencyMatch.class);
        SsAdjacencyMatch duplicateA = mock(SsAdjacencyMatch.class);

        when(matchA.signBlock()).thenReturn(blockA);
        when(matchB.signBlock()).thenReturn(blockB);
        when(duplicateA.signBlock()).thenReturn(blockA);

        when(rule1.findMatches(query)).thenReturn(List.of(matchA, matchB));
        when(rule2.findMatches(query)).thenReturn(List.of(duplicateA));

        List<SsAdjacencyMatch> resolved = resolver.findAll(query);

        assertEquals(List.of(matchA, matchB), resolved);
    }
}
