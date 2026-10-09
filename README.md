# Kita

Java 25 / JDA / MariaDB / Lavalinkで構成する、複数の物事に柔軟に対応可能な多機能BOT

## 起動

1. `.env.example`を`.env`へ、`config/kita.example.yml`を`config/kita.yml`へコピー。
2. `.env`にDiscordトークンとDB・Lavalinkのパスワードを設定。翻訳を使う場合はDeepLキーも設定。
3. HRIRのWAVを`presets/`へ配置。サブフォルダも使用できます。
4. Ubuntuでは`sudo chown -R 10001:10001 data`で保存先の書き込み権限を設定。
5. `docker compose up -d --build`で起動。
6. `/settings`で機能を有効化。初期状態ではプレビュー・翻訳・チケット・LogShareは無効です。

Discord Developer Portalで**Message Content Intent**を有効にしてください。
招待スコープは`bot`と`applications.commands`を使用します。
必要なBot権限・開発手順は[運用手順](docs/operations.md)を参照してください。

## コマンド

| コマンド | 用途 |
| --- | --- |
| `/ping` | Gateway・RESTの応答速度 |
| `/avatar user: server:` | ユーザーまたはサーバーアバター |
| `/fastfetch` | JVM・OS・CPU・稼働時間 |
| `/settings` | サーバー別の設定パネル |
| `/ticket` | 問い合わせ受付 |
| `/play query:` | URL・検索・公開プレイリスト |
| `/stop` `/skip` | 停止・次の曲 |
| `/player` | 音楽操作パネル |

補助プレフィックスは`k!`です。例: `k!ping`、`k!play 曲名`、`k!avatar ユーザーID server`。

## LogShare

`/settings`のLogShareで機能を有効にすると、メッセージメニューの「mclo.gsで共有」または📋でログを共有できます。
手動共有は投稿者本人・サーバー管理者・設定した担当ロールが利用でき、メッセージ本文にも対応します。
添付ログの自動共有を有効にし、対象チャンネルを選ぶと、Minecraft/Hytaleと判定できた`.log`・`.txt`だけを共有します。
フォーラムを選んだ場合は配下のスレッドも対象になります。元の投稿を残して、リンクをサイレント返信します。
APIキーは不要です。最大10MiB・25,000行・1投稿5ファイルまでで、上限を超えた内容を自動で省略しません。

## License

[Blue Oak Model License 1.0.0](LICENSE.md)

## Credits

[JDA](https://github.com/discord-jda/JDA), [Lavalink](https://github.com/lavalink-devs/Lavalink),
[LavaSrc](https://github.com/topi314/LavaSrc), [youtube-source](https://github.com/lavalink-devs/youtube-source),
[DeepL Java SDK](https://github.com/DeepLcom/deepl-java), [JTransforms](https://github.com/wendykierp/JTransforms),
[mclogs-java](https://github.com/aternosorg/mclogs-java),
[FFmpeg](https://ffmpeg.org/), [yt-dlp](https://github.com/yt-dlp/yt-dlp),
[TokenEnvoy](https://github.com/CleanroomMC/TokenEnvoy), [Versioning](https://github.com/CleanroomMC/Versioning).
