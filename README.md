# Kita

Java 25 / JDA / MariaDB / Lavalinkで構成する、複数の物事に柔軟に対応可能な多機能BOT

## 起動

1. `.env.example`を`.env`へ、`config/kita.example.yml`を`config/kita.yml`へコピー。
2. `.env`にDiscordトークンとDB・Lavalinkのパスワードを設定。翻訳を使う場合はDeepLキーも設定。
3. HRIRのWAVを`presets/`へ配置。サブフォルダも使用できます。
4. Ubuntuでは`sudo chown -R 10001:10001 data`で保存先の書き込み権限を設定。
5. `docker compose up -d --build`で起動。
6. `/settings`で機能を有効化。初期状態ではプレビュー・翻訳・チケットは無効です。

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

## License

[Blue Oak Model License 1.0.0](LICENSE.md)

## Credits

[JDA](https://github.com/discord-jda/JDA), [Lavalink](https://github.com/lavalink-devs/Lavalink),
[LavaSrc](https://github.com/topi314/LavaSrc), [youtube-source](https://github.com/lavalink-devs/youtube-source),
[DeepL Java SDK](https://github.com/DeepLcom/deepl-java), [JTransforms](https://github.com/wendykierp/JTransforms),
[FFmpeg](https://ffmpeg.org/), [yt-dlp](https://github.com/yt-dlp/yt-dlp),
[TokenEnvoy](https://github.com/CleanroomMC/TokenEnvoy), [Versioning](https://github.com/CleanroomMC/Versioning).
