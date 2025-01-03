import json
import yt_dlp

def getInfo(videoID):
    ytdlp_opts = {
        "extractor_args": {
            "youtube": {
                "player_client": ["mweb"]
            }
        },
        "format": "bestvideo[ext=mp4]+bestaudio[ext=m4a]",
        "noplaylist": True
    }

    with yt_dlp.YoutubeDL(ytdlp_opts) as ytdlp:
        info = ytdlp.extract_info(f"https://www.youtube.com/watch?v={videoID}", download=False)
        return json.dumps(ytdlp.sanitize_info(info))