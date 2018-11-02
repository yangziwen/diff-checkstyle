package io.github.yangziwen.checkstyle.diff;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.OrTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import lombok.Builder;
import lombok.Getter;

/**
 * The diff calculator
 * calculate the diff entries and the corresponding edits between the sepcifed 2 revisions
 *
 * @author yangziwen
 */
@Getter
@Builder
public class DiffCalculator {

    private DiffAlgorithm diffAlgorithm;

    @Builder.Default
    private RawTextComparator comparator = RawTextComparator.DEFAULT;

    @Builder.Default
    private int bigFileThreshold = DiffHelper.DEFAULT_BIG_FILE_THRESHOLD;

    /**
     * calculate the diff between the old revision and the new revision
     *
     * @param repoDir    the git directory
     * @param oldRev    the old revision
     * @param newRev    the new revision
     * @return
     * @throws Exception
     */
    public List<DiffEntryWrapper> calculateDiff(File repoDir, String oldRev, String newRev, boolean includeIndexedCodes) throws Exception {
        try (Git git = Git.open(repoDir);
                ObjectReader reader = git.getRepository().newObjectReader();
                RevWalk rw = new RevWalk(git.getRepository())) {

            RenameDetector detector = new RenameDetector(git.getRepository());

            RevCommit oldCommit = rw.parseCommit(git.getRepository().resolve(oldRev));
            RevCommit newCommit = rw.parseCommit(git.getRepository().resolve(newRev));
            AbstractTreeIterator oldTree = new CanonicalTreeParser(null, reader, oldCommit.getTree());
            AbstractTreeIterator newTree = new CanonicalTreeParser(null, reader, newCommit.getTree());

            List<DiffEntry> entries = git.diff()
                    .setOldTree(oldTree)
                    .setNewTree(newTree)
                    .call();
            detector.reset();
            detector.addAll(entries);
            entries = detector.compute();

            List<DiffEntryWrapper> wrapperList = new ArrayList<>();

            Set<String> indexedFilePathSet = new HashSet<>();
            if (includeIndexedCodes) {
                Status status = git.status().call();
                indexedFilePathSet.addAll(status.getAdded());
                indexedFilePathSet.addAll(status.getChanged());
                Map<String, byte[]> indexedFileContentMap = getIndexedFileContentMap(git, indexedFilePathSet);
                Map<String, byte[]> oldRevFileContentMap = getRevFileContentMap(git, oldCommit, indexedFilePathSet, reader);
                List<DiffEntryWrapper> wrappers = indexedFilePathSet.stream()
                        .map(filePath -> {
                            byte[] oldBytes = oldRevFileContentMap.get(filePath);
                            RawText oldText = oldBytes != null ? new RawText(oldBytes) : RawText.EMPTY_TEXT;
                            RawText newText = new RawText(indexedFileContentMap.get(filePath));
                            DiffEntry entry = oldBytes == null
                                    ? DiffHelper.createAddDiffEntry(filePath, oldCommit)
                                    : DiffHelper.createModifyDiffEntry(filePath);
                            return DiffEntryWrapper.builder()
                                    .gitDir(repoDir)
                                    .diffEntry(entry)
                                    .editList(calculateEditList(oldText, newText))
                                    .build();
                        })
                        .collect(Collectors.toList());
                wrapperList.addAll(wrappers);
            }

            for (DiffEntry entry : entries) {
                if (indexedFilePathSet.contains(entry.getNewPath())) {
                    continue;
                }
                RawText oldText = newRawText(entry, DiffEntry.Side.OLD, reader);
                RawText newText = newRawText(entry, DiffEntry.Side.NEW, reader);
                DiffEntryWrapper wrapper = DiffEntryWrapper.builder()
                        .gitDir(repoDir)
                        .diffEntry(entry)
                        .editList(calculateEditList(oldText, newText))
                        .build();
                wrapperList.add(wrapper);
            }

            return wrapperList;
        }
    }

    private Map<String, byte[]> getRevFileContentMap(
            Git git, RevCommit commit, Set<String> filePathSet, ObjectReader reader) throws Exception {
        if (CollectionUtils.isEmpty(filePathSet)) {
            return Collections.emptyMap();
        }
        TreeFilter filter = filePathSet.size() > 1
                ? OrTreeFilter.create(filePathSet.stream()
                        .map(PathFilter::create)
                        .collect(Collectors.toList()))
                : PathFilter.create(filePathSet.iterator().next());
         return getContentMapByTreeAndFilter(git, new CanonicalTreeParser(null, reader, commit.getTree()), filter);
    }

    private Map<String, byte[]> getIndexedFileContentMap(Git git, Set<String> filePathSet) throws Exception {
        if (CollectionUtils.isEmpty(filePathSet)) {
            return Collections.emptyMap();
        }
        DirCache index = git.getRepository().readDirCache();
        TreeFilter filter = filePathSet.size() > 1
                ? OrTreeFilter.create(filePathSet.stream()
                        .map(PathFilter::create)
                        .collect(Collectors.toList()))
                : PathFilter.create(filePathSet.iterator().next());
        return getContentMapByTreeAndFilter(git, new DirCacheIterator(index), filter);
    }

    private Map<String, byte[]> getContentMapByTreeAndFilter(
            Git git, AbstractTreeIterator tree, TreeFilter filter) throws Exception {
        Map<String, byte[]> contentMap = new LinkedHashMap<>();
        try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(filter);
            while (treeWalk.next()) {
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = git.getRepository().open(objectId);
                contentMap.put(treeWalk.getPathString(), loader.getBytes());
            }
        }
        return contentMap;
    }

    private RawText newRawText(DiffEntry entry, DiffEntry.Side side, ObjectReader reader) {
        try {
            return new RawText(DiffHelper.open(entry, side, reader, bigFileThreshold));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Edit> calculateEditList(RawText oldText, RawText newText) {
        EditList edits = diffAlgorithm.diff(comparator, oldText, newText);
        List<Edit> editList = new ArrayList<Edit>();
        for (Edit edit : edits) {
            editList.add(edit);
        }
        return editList;
    }

}
