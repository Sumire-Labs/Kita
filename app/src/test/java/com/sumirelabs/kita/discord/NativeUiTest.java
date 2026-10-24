package com.sumirelabs.kita.discord;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.discord.ui.Choices;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.TextDisplay;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeUiTest {
    @Test void nativePayloadPreservesSilentComponentsV2AndAttachments() {
        var menu = Choices.create("choice").addOption("One", "1").addOption("Two", "2").setDefaultValues("2").build();
        var message = Ui.message(List.of(Container.of(TextDisplay.of("Header"), ActionRow.of(menu))));
        var request = java.util.Objects.requireNonNull(message.asRequest().getJsonPayload());
        assertEquals(36864, request.flags().get());
        assertEquals(17, request.components().get().getFirst().type());
        assertEquals(1, Ui.file(message, "test.txt", new byte[]{1}).asRequest().getFiles().size());
        assertEquals("Header", request.components().get().getFirst().components().get().getFirst().content().get());
    }
    @Test void followupsKeepFlagsFilesAndExplicitlySuppressReplyMentions() {
        var message = Ui.file(Ui.text("LogShare", "Saved"), "latest.log", new byte[]{1});
        for (boolean ephemeral : new boolean[]{true, false}) {
            var multipart = Replies.followupRequest(message, ephemeral);
            var payload = java.util.Objects.requireNonNull(multipart.getJsonPayload());
            assertEquals(36864 | (ephemeral ? 64 : 0), payload.flags().get());
            assertFalse(payload.allowedMentions().get().repliedUser().get());
            assertTrue(payload.allowedMentions().get().parse().get().isEmpty());
            assertEquals(17, payload.components().get().getFirst().type());
            assertEquals(1, multipart.getFiles().size());
        }
        var callback = java.util.Objects.requireNonNull(Replies.callback(message).asRequest().getJsonPayload());
        assertEquals(32768 | 64, callback.flags().get());
        assertFalse(callback.allowedMentions().get().repliedUser().get());
    }
}
