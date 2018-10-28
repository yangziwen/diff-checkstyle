package io.github.yangziwen.checkstyle.filter;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.collections.CollectionUtils;
import org.eclipse.jgit.diff.Edit;

import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AutomaticBean;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Filter;

import io.github.yangziwen.checkstyle.diff.DiffEntryWrapper;

/**
 * The diff line filter
 *
 * @author yangziwen
 */
public class DiffLineFilter extends AutomaticBean implements Filter {

    private final ConcurrentMap<String, List<Edit>> fileEditMap = new ConcurrentHashMap<>();

    public DiffLineFilter(List<DiffEntryWrapper> diffEntryList) {
        for (DiffEntryWrapper diffEntry : diffEntryList) {
            fileEditMap.put(diffEntry.getAbsoluteNewPath(), diffEntry.getEditList());
        }
    }

    /**
     * Only accept events that corresponding to the diff edits
     */
    @Override
    public boolean accept(AuditEvent event) {
        List<Edit> editList = fileEditMap.get(event.getFileName());
        if (CollectionUtils.isEmpty(editList)) {
            return false;
        }
        for (Edit edit : editList) {
            if (edit.getType() == Edit.Type.DELETE) {
                continue;
            }
            if (edit.getBeginB() < event.getLine() && edit.getEndB() >= event.getLine()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void finishLocalSetup() throws CheckstyleException {
        // do nothing
    }

}
