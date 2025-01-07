import json
import yt_dlp

def getInfo(videoID):
    ytdlp_opts = {
        "extractor_args": {
            "youtube": {
                "player_client": ["ios","mweb"]
            }
        },
        "format": "bestvideo[protocol=m3u8_native]+bestaudio[protocol=https][ext=m4a]/bestvideo[protocol=m3u8_native]",
        "ignore_no_formats_error": True,
        "noplaylist": True
    }

    info = {}

    with yt_dlp.YoutubeDL(ytdlp_opts) as ytdlp:
        x = ytdlp.extract_info(f"https://www.youtube.com/watch?v={videoID}", download=False)
        y = json.loads(json.dumps(ytdlp.sanitize_info(x)))
        info["id"] = y["id"]
        info["title"] = y["title"]
        info["author"] = y["uploader"]
        info["artwork"] = y["thumbnail"]
        info["views"] = y["view_count"]
        info["likes"] = y["like_count"]
        info["live"] = y["is_live"]
        if ("requested_formats" in y):
            info["hlsUrl"] = y["requested_formats"][0]["manifest_url"]
            info["audioUrl"] = y["requested_formats"][1]["url"]
        if ("requested_formats" not in y):
            info["hlsUrl"] = y["manifest_url"]
            info["audioUrl"] = None
        
    return json.dumps(info)