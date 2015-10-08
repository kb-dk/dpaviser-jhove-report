package dk.statsbiblioteket.dpaviser.report.helpers;

import java.util.Comparator;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Comparator of "rows" of "cells" (i.e. list of strings).  Use the first result where left.get(i) is not equal to
 * right.get(i).  If one list is the subset of the other, the shortest list is first.
 */
public class CellRowComparator implements Comparator<List<String>> {
    @Override
    public int compare(List<String> left, List<String> right) {

        int itemsInShortestList = Math.min(left.size(), right.size());

        for (int i = 0; i < itemsInShortestList; i++) {
            String leftValue = left.get(i);
            int comparison = leftValue.compareTo(right.get(i));
            if (comparison != 0) {
                return comparison;
            }
        }
        // one list exhausted without a difference - the shortest is "before" the longest.
        return right.size() - left.size();
    }
}
