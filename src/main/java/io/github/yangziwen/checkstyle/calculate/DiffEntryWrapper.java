package io.github.yangziwen.checkstyle.calculate;

import java.io.File;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.Edit;

import lombok.Builder;
import lombok.Data;

/**
 * The diff entry wrapper
 *
 * @author yangziwen
 */
@Data
@Builder
public class DiffEntryWrapper {

    private File gitDir;

    private DiffEntry diffEntry;

    private List<Edit> editList;

    /**
     * Determines whether there are only delete operations in this file
     * @return
     */
    public boolean isDeleteOnly() {
        if (diffEntry.getChangeType() == ChangeType.DELETE) {
            return true;
        }
        return editList.stream().allMatch(edit -> edit.getType() == Edit.Type.DELETE);
    }

    public String getAbsoluteNewPath() {
        return getNewFile().getAbsolutePath();
    }

    public File getNewFile() {
        return new File(gitDir, diffEntry.getNewPath());
    }

}
