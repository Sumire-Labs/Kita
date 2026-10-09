package com.sumirelabs.kita.stay;

import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

final class StayPanel {
    private StayPanel() {}
    static MessageCreateData render(StaySession.View view) {
        if (!view.active()) return Ui.text("Stay", "常駐を終了しました。/stay で再び開始できます。");
        String limit = view.until() == null ? "無期限（もう一度 /stay するまで）"
                : "<t:" + view.until().getEpochSecond() + ":F>（<t:" + view.until().getEpochSecond() + ":R>）";
        String prefix = "stay:" + view.token() + ":";
        return Ui.message(List.of(Container.of(
                TextDisplay.of("## Stay\n<#" + view.voiceChannel() + "> に常駐中\n**退出予定**：" + limit
                        + "\n無人・音楽停止でも残ります。時間切れや退出操作では音楽も停止します。"),
                ActionRow.of(Button.primary(prefix + "duration", "退出までの時間"),
                        Button.secondary(prefix + "unlimited", "無期限に戻す"), Button.danger(prefix + "stop", "退出")))));
    }
    static Modal modal(String token) {
        return Modal.create("stay-time:" + token, "退出までの時間")
                .addComponents(Label.of("時間（0〜999999）", TextInput.create("hours", TextInputStyle.SHORT)
                                .setValue("0").setMaxLength(6).build()),
                        Label.of("分（0〜59）", TextInput.create("minutes", TextInputStyle.SHORT)
                                .setValue("30").setMaxLength(2).build())).build();
    }
}
