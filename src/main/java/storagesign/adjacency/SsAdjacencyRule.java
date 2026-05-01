package storagesign.adjacency;

import java.util.List;

/**
 * 1 種類の看板配置ルールに基づいて隣接 SS を検出する戦略インターフェース。
 */
public interface SsAdjacencyRule {

    /**
     * ルールに一致する候補を返す。
     */
    List<SsAdjacencyMatch> findMatches(SsAdjacencyQuery query);
}
