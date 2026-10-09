package com.sumirelabs.kita.previews;

public interface PreviewProvider {
    Preview fetch(SocialLink link) throws Exception;
}
