package storagesign.adjacency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.block.Block;

/**
 * ルール群をまとめて隣接 StorageSign を解決する。
 */
public final class SsAdjacencyResolver {

    private final List<SsAdjacencyRule> rules;

    public SsAdjacencyResolver(List<SsAdjacencyRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static SsAdjacencyResolver defaultResolver() {
        return new SsAdjacencyResolver(List.of(
            new StandingAndCeilingHangingRule(),
            new WallSignBackFaceRule(),
            new WallHangingSideFacesRule()
        ));
    }

    /**
     * ルール順で最初に一致した候補を返す。
     */
    public Optional<SsAdjacencyMatch> findFirst(SsAdjacencyQuery query) {
        for (SsAdjacencyRule rule : rules) {
            Optional<SsAdjacencyMatch> match = rule.findFirstMatch(query);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    /**
     * すべての一致候補を返す。重複ブロックは最初の一致を優先する。
     */
    public List<SsAdjacencyMatch> findAll(SsAdjacencyQuery query) {
        Map<Block, SsAdjacencyMatch> dedup = new LinkedHashMap<>();
        for (SsAdjacencyRule rule : rules) {
            for (SsAdjacencyMatch match : rule.findMatches(query)) {
                dedup.putIfAbsent(match.signBlock(), match);
            }
        }
        return new ArrayList<>(dedup.values());
    }
}
