package com.sumirelabs.kita.stay;

import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.Container;
import com.sumirelabs.kita.discord.Forms;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.component.TextInput;

import discord4j.core.spec.InteractionPresentModalSpec;
import discord4j.core.spec.MessageCreateSpec;

final class StayPanel {
    private StayPanel() {}
    static MessageCreateSpec render(StaySession.View view) {
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
    static InteractionPresentModalSpec modal(String token) {
        return Forms.modal("stay-time:" + token, "退出までの時間",
                Forms.input("hours", "時間（0〜999999）", 6, "0"),
                Forms.input("minutes", "分（0〜59）", 2, "30"));
    }
}
