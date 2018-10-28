package io.github.yangziwen.checkstyle.calculate;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.ContentSource;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import lombok.Builder;
import lombok.Getter;

/**
 * The diff edit calculator
 * calculate the diff entries and the corresponding edits between the sepcifed 2 revisions
 *
 * @author yangziwen
 */
@Getter
@Builder
public class DiffEditCalculator {

    private static final int DEFAULT_BIG_FILE_THRESHOLD = 10 * 1024 * 1024;

    private static final Field DIFF_ENTRY_OLD_ID_FIELD = getField(DiffEntry.class, "oldId");

    private static final Field DIFF_ENTRY_NEW_ID_FIELD = getField(DiffEntry.class, "newId");

    private static final byte[] EMPTY = new byte[] {};

    private static final byte[] BINARY = new byte[] {};

    private DiffAlgorithm diffAlgorithm;

    @Builder.Default
    private RawTextComparator comparator = RawTextComparator.DEFAULT;

    @Builder.Default
    private int bigFileThreshold = DEFAULT_BIG_FILE_THRESHOLD;

    /**
     * calculate the diff between the old revision and the new revision
     *
     * @param repoDir	the git directory
     * @param oldRev	the old revision
     * @param newRev	the new revision
     * @return
     * @throws Exception
     */
    public List<DiffEntryWrapper> calculateDiff(File repoDir, String oldRev, String newRev) throws Exception {
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

            for (DiffEntry entry : entries) {
                DiffEntryWrapper wrapper = DiffEntryWrapper.builder()
                        .gitDir(repoDir)
                        .diffEntry(entry)
                        .editList(calculateEditList(entry, reader))
                        .build();
                wrapperList.add(wrapper);
            }

            return wrapperList;
        }
    }

    private List<Edit> calculateEditList(DiffEntry entry, ObjectReader reader) throws Exception {
        RawText oldText = new RawText(open(DiffEntry.Side.OLD, entry, reader));
        RawText newText = new RawText(open(DiffEntry.Side.NEW, entry, reader));
        EditList edits = diffAlgorithm.diff(comparator, oldText, newText);
        List<Edit> editList = new ArrayList<Edit>();
        for (Edit edit : edits) {
            editList.add(edit);
        }
        return editList;
    }

    private byte[] open(DiffEntry.Side side, DiffEntry entry, ObjectReader reader) throws Exception {
        if (entry.getMode(side) == FileMode.GITLINK) {
            return writeGitLinkText(entry.getId(side));
        }
        if (entry.getMode(side) == FileMode.MISSING) {
            return EMPTY;
        }
        if (entry.getMode(side).getObjectType() != Constants.OBJ_BLOB) {
            return EMPTY;
        }
        AbbreviatedObjectId id = entry.getId(side);
        if (!id.isComplete()) {
            Collection<ObjectId> ids = reader.resolve(id);
            if (ids.size() == 1) {
                id = AbbreviatedObjectId.fromObjectId(ids.iterator().next());
                switch (side) {
                    case OLD:
                        DIFF_ENTRY_OLD_ID_FIELD.set(entry, id);
                        break;
                    case NEW:
                        DIFF_ENTRY_NEW_ID_FIELD.set(entry, id);
                        break;
                    default:
                        break;
                }
            }
            else if (ids.size() == 0) {
                throw new MissingObjectException(id, Constants.OBJ_BLOB);
            }
            else {
                throw new AmbiguousObjectException(id, ids);
            }
        }
        ContentSource cs = ContentSource.create(reader);
        try {
            ObjectLoader ldr = new ContentSource.Pair(cs, cs).open(side, entry);
            return ldr.getBytes(bigFileThreshold);

        } catch (LargeObjectException.ExceedsLimit overLimit) {
            return BINARY;

        } catch (LargeObjectException.ExceedsByteArrayLimit overLimit) {
            return BINARY;

        } catch (LargeObjectException.OutOfMemory tooBig) {
            return BINARY;

        } catch (LargeObjectException tooBig) {
            tooBig.setObjectId(id.toObjectId());
            throw tooBig;
        }
    }

    private static byte[] writeGitLinkText(AbbreviatedObjectId id) {
        if (ObjectId.zeroId().equals(id.toObjectId())) {
            return EMPTY;
        }
        return Constants.encodeASCII("Subproject commit " + id.name() + "\n");
    }

    private static <T> Field getField(Class<T> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
