package io.github.yangziwen.checkstyle.diff;

import org.eclipse.jgit.lib.AnyObjectId;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BlobWrapper {

    private AnyObjectId blobId;

    private byte[] content;

}
