import json
import re
import yt_dlp

def getInfo(runtime, videoID, searchQuery):
    ytdlp_opts = {
        "cachedir": False,
        "check_formats": "selected",
        "format": "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]",
        "js_runtimes": {
            "deno": {
                "path": None
            },
            "quickjs": {
                "path": runtime
            }
        },
        "noplaylist": True,
        "remote_components": [
            "ejs:github"
        ]
    }

    info = {}
    with yt_dlp.YoutubeDL(ytdlp_opts) as ytdlp:
        if (searchQuery == None):
            x = ytdlp.extract_info(f"https://www.youtube.com/watch?v={videoID}", download=False)
            y = json.loads(json.dumps(ytdlp.sanitize_info(x)))
        if (videoID == None):
            x = ytdlp.extract_info(f"ytsearch:{searchQuery}", download=False)
            z = json.loads(json.dumps(ytdlp.sanitize_info(x)))
            y = z["entries"][0]

        a = ytdlp.urlopen(f"https://www.youtube.com/watch?v={y["id"]}").read().decode("utf-8")
        b = re.search(r"ytInitialPlayerResponse\s*=\s*({.+?});", a)
        if b:
            c = json.loads(b.group(1))
            d = c["streamingData"]["adaptiveFormats"]
            e = {str(f["itag"]): f for f in d}
            for f in y["requested_formats"]:
                itag = str(f["format_id"]).split("-")[0]
                f["indexRange"] = e[itag]["indexRange"]
                f["initRange"] = e[itag]["initRange"]

        info["id"] = y["id"]
        info["title"] = y["title"]
        info["author"] = y["uploader"]
        info["artwork"] = y["thumbnail"]
        info["live"] = y["is_live"]
        info["views"] = y["view_count"]
        info["likes"] = y["like_count"]
        info["type"] = y["media_type"]
        info["expiration"] = "100000000000000"
        info["videoDuration"] = y["duration"]
        if ("requested_formats" in y):
            info["videoUrl"] = y["requested_formats"][0]["url"]
            info["videoIndexStart"] = y["requested_formats"][0]["indexRange"]["start"]
            info["videoIndexEnd"] = y["requested_formats"][0]["indexRange"]["end"]
            info["videoInitStart"] = y["requested_formats"][0]["initRange"]["start"]
            info["videoInitEnd"] = y["requested_formats"][0]["initRange"]["end"]
            info["videoCodec"] = y["requested_formats"][0]["vcodec"]
            info["videoExt"] = y["requested_formats"][0]["ext"]
            info["videoHeight"] = y["requested_formats"][0]["height"]
            info["videoWidth"] = y["requested_formats"][0]["width"]
            info["audioUrl"] = y["requested_formats"][1]["url"]
            info["audioIndexStart"] = y["requested_formats"][1]["indexRange"]["start"]
            info["audioIndexEnd"] = y["requested_formats"][1]["indexRange"]["end"]
            info["audioInitStart"] = y["requested_formats"][1]["initRange"]["start"]
            info["audioInitEnd"] = y["requested_formats"][1]["initRange"]["end"]
            info["audioCodec"] = y["requested_formats"][1]["acodec"]
            info["audioExt"] = y["requested_formats"][1]["ext"]
            info["hlsUrl"] = None
        else:
            info["videoUrl"] = None
            info["videoIndexStart"] = None
            info["videoIndexEnd"] = None
            info["videoInitStart"] = None
            info["videoInitEnd"] = None
            info["videoCodec"] = None
            info["videoExt"] = None
            info["videoHeight"] = None
            info["videoWidth"] = None
            info["audioUrl"] = None
            info["audioIndexStart"] = None
            info["audioIndexEnd"] = None
            info["audioInitStart"] = None
            info["audioInitEnd"] = None
            info["audioCodec"] = None
            info["audioExt"] = None
            info["hlsUrl"] = y["url"]
        subtitles = []
        for a in y["subtitles"]:
            c = {}
            for b in y["subtitles"][a]:
                if (b["ext"] == "vtt"):
                    c["id"] = a
                    c["name"] = b["name"]
                    c["url"] = b["url"]
            if (len(c) != 0):
                subtitles.append(c)
        # if (len(subtitles) == 0):
        info["subtitles"] = None
        # else:
        # info["subtitles"] = subtitles
        
    return json.dumps(info)