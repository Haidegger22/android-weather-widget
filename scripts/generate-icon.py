#!/usr/bin/env python3
"""Генерация картинки-иконки через MiniMax (модель image-01).

Ключ берётся из переменной окружения MINIMAX_API_KEY — в коде и репозитории его нет.

Запуск:
    MINIMAX_API_KEY=... python3 scripts/generate-icon.py --out icons
Каждый вариант сохраняется отдельным файлом: icons/icon_a.png и т.д.
"""
import argparse
import base64
import json
import os
import sys
import urllib.request

HOST = "https://api.minimax.io/v1/image_generation"

# Несколько вариантов — чтобы было из чего выбрать
VARIANTS = {
    "a": ("App icon, rounded square, deep blue to indigo gradient background, "
          "minimal white sun with soft warm glow, small soft white cloud below it, "
          "flat vector style, centered, no text, clean crisp edges, modern, high contrast"),
    "b": ("App icon, night navy gradient sky background, large elegant white crescent moon, "
          "small warm golden sun peeking from behind, minimal flat vector, centered, no text, elegant"),
    "c": ("App icon, gradient sky blue to deep navy, bold minimalist white sun with a thin "
          "cloud, flat vector, simple strong shapes, centered, no text, high contrast, clean"),
}


def api_key():
    key = os.environ.get("MINIMAX_API_KEY", "").strip()
    if not key:
        sys.exit("Нужна переменная окружения MINIMAX_API_KEY")
    return key


def generate(prompt, path):
    body = json.dumps({
        "model": "image-01",
        "prompt": prompt,
        "aspect_ratio": "1:1",
        "response_format": "base64",
        "n": 1,
        "prompt_optimizer": True,
    }).encode()
    req = urllib.request.Request(HOST, data=body, headers={
        "Authorization": "Bearer " + api_key(),
        "Content-Type": "application/json",
    })
    with urllib.request.urlopen(req, timeout=180) as resp:
        data = json.loads(resp.read())
    status = (data.get("base_resp") or {}).get("status_code")
    if status != 0:
        print(f"  ошибка API: {status} {(data.get('base_resp') or {}).get('status_msg')}")
        return False
    payload = data.get("data") or {}
    images = payload.get("image_base64") or []
    if images:
        raw = base64.b64decode(images[0])
    elif payload.get("image_urls"):
        with urllib.request.urlopen(payload["image_urls"][0], timeout=120) as r:
            raw = r.read()
    else:
        print("  пустой ответ:", json.dumps(data)[:200])
        return False
    with open(path, "wb") as fh:
        fh.write(raw)
    print(f"  {path} ({len(raw) // 1024} КБ)")
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="icons", help="куда сохранять картинки")
    ap.add_argument("--variants", default="a,b,c", help="какие варианты генерировать")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    for name in args.variants.split(","):
        name = name.strip()
        if name not in VARIANTS:
            continue
        print(f"  вариант {name}: генерирую…")
        generate(VARIANTS[name], os.path.join(args.out, f"icon_{name}.png"))


if __name__ == "__main__":
    main()
