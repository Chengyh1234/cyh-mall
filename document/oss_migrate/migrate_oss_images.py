# -*- coding: utf-8 -*-
"""
mall 项目存量 OSS 图片批量迁移脚本
====================================

背景
----
mall.sql 初始化数据中的图片 URL 指向原作者 OSS 桶：
    http(s)://macro-oss.oss-cn-shenzhen.aliyuncs.com/mall/images/...
本脚本负责把这些图片批量"下载 -> 上传"到你自己的 OSS 桶，
并保持 mall/images/... 原目录结构不变，之后只需替换数据库中的域名即可。

前置条件
--------
1. 已创建自己的阿里云 OSS Bucket（例如 mall-oss-cyh，北京区域）
2. 拥有该 Bucket 的 AccessKeyId / AccessKeySecret（只读 + 写入权限）
3. 安装依赖：
       pip install oss2 requests

用法
----
    python migrate_oss_images.py \
        --access-key-id LTAIxxxxxxxx \
        --access-key-secret xxxxxxxx \
        --endpoint oss-cn-beijing.aliyuncs.com \
        --bucket mall-oss-cyh

    # 只预览待迁移清单，不实际执行：
    python migrate_oss_images.py ... --dry-run

    # AccessKey 也可通过环境变量传入，避免明文：
    set OSS_AK_ID=LTAIxxxxxxxx
    set OSS_AK_SECRET=xxxxxxxx
    python migrate_oss_images.py --endpoint oss-cn-beijing.aliyuncs.com --bucket mall-oss-cyh

可选参数
--------
    --sql <path>   mall.sql 文件路径（默认自动在项目 document/sql 下查找）
    --dry-run      仅输出待迁移清单，不下载不上传
    --timeout <s>  单个请求超时秒数（默认 30）
"""

import argparse
import os
import re
import sys
from urllib.parse import urlparse

import requests

SRC_DOMAIN = "macro-oss.oss-cn-shenzhen.aliyuncs.com"


def find_sql_file(cli_path):
    if cli_path:
        return cli_path
    here = os.path.dirname(os.path.abspath(__file__))
    candidates = [
        os.path.join(here, "..", "sql", "mall.sql"),
        os.path.join(here, "..", "..", "document", "sql", "mall.sql"),
        os.path.join(here, "mall.sql"),
    ]
    for c in candidates:
        if os.path.isfile(c):
            return c
    sys.exit(f"[错误] 未找到 mall.sql，请用 --sql 指定路径")


def extract_image_urls(sql_path):
    """从 mall.sql 提取所有引用源桶的唯一图片 URL

    终止符只用引号（' 和 \"）——文件名可能含空格、括号、逗号，
    逗号拼接的相册串会整体提取后再拆分。
    """
    with open(sql_path, encoding="utf-8") as f:
        content = f.read()
    raw = set(re.findall(r"https?://" + re.escape(SRC_DOMAIN) + r"/[^'\"\\]*", content))
    urls = set()
    for u in raw:
        for part in u.split(","):
            part = part.strip()
            if part:
                urls.add(part)
    return sorted(urls)


def fetch_image(url, timeout):
    """下载源图片，返回 (bytes, content_type)"""
    resp = requests.get(url, timeout=timeout)
    resp.raise_for_status()
    ctype = resp.headers.get("Content-Type", "").split(";")[0].strip().lower()
    return resp.content, ctype


def guess_content_type(key):
    ext = os.path.splitext(key)[1].lower()
    return {
        ".jpg": "image/jpeg", ".jpeg": "image/jpeg",
        ".png": "image/png", ".gif": "image/gif",
        ".webp": "image/webp", ".bmp": "image/bmp",
    }.get(ext, "application/octet-stream")


def main():
    ap = argparse.ArgumentParser(description="mall 存量 OSS 图片迁移")
    ap.add_argument("--access-key-id", default=os.environ.get("OSS_AK_ID", ""))
    ap.add_argument("--access-key-secret", default=os.environ.get("OSS_AK_SECRET", ""))
    ap.add_argument("--endpoint", required=True, help="你的 OSS 对外访问域名，如 oss-cn-beijing.aliyuncs.com")
    ap.add_argument("--bucket", required=True, help="你的 OSS 桶名，如 mall-oss-cyh")
    ap.add_argument("--sql", default=None, help="mall.sql 路径（可选）")
    ap.add_argument("--dry-run", action="store_true", help="仅预览清单")
    ap.add_argument("--timeout", type=int, default=30)
    args = ap.parse_args()

    if not args.dry_run and (not args.access_key_id or not args.access_key_secret):
        sys.exit("[错误] 必须提供 AccessKey：--access-key-id/--access-key-secret 或环境变量 OSS_AK_ID/OSS_AK_SECRET")

    try:
        import oss2
    except ImportError:
        if args.dry_run:
            oss2 = None
        else:
            sys.exit("[错误] 缺少依赖 oss2，请先执行：pip install oss2 requests")

    sql_path = find_sql_file(args.sql)
    urls = extract_image_urls(sql_path)
    print(f"[信息] 从 {os.path.basename(sql_path)} 提取到 {len(urls)} 张唯一图片")

    # 本地/内存临时对象存储，避免落盘
    images = {}
    if not args.dry_run:
        print(f"[信息] 开始下载源图片（源桶: {SRC_DOMAIN}）...")
        failed = []
        for u in urls:
            try:
                data, ctype = fetch_image(u, args.timeout)
                images[u] = (data, ctype)
            except Exception as e:
                failed.append((u, str(e)))
                print(f"  [下载失败] {u} -> {e}")
        if failed:
            print(f"[警告] {len(failed)} 张下载失败，将跳过上传")
        print(f"[信息] 下载完成，成功 {len(images)} 张")

    if args.dry_run:
        print("\n========== 待迁移清单（--dry-run 预览） ==========")
        for u in urls:
            key = urlparse(u).path.lstrip("/")
            print(f"  [待迁移] {key}  <== {u}")
        print(f"\n共 {len(urls)} 张，预览模式未执行任何上传")
        print("下一步：执行 document/oss_migrate/update_image_urls.sql 替换数据库 URL 域名")
        return

    auth = oss2.Auth(args.access_key_id, args.access_key_secret)
    bucket = oss2.Bucket(auth, args.endpoint, args.bucket)

    uploaded, skipped, failed = 0, 0, []
    for u in urls:
        key = urlparse(u).path.lstrip("/")  # 保持 mall/images/... 原路径
        if not args.dry_run:
            if bucket.object_exists(key):
                skipped += 1
                continue
            data, ctype = images.get(u, (None, None))
            if data is None:
                failed.append(u)
                continue
            bucket.put_object(key, data, headers={"Content-Type": ctype or guess_content_type(key)})
            uploaded += 1
            print(f"  [已上传] {key}")
        else:
            print(f"  [待迁移] {u}")

    print("\n========== 迁移结果 ==========")
    print(f"总图片数   : {len(urls)}")
    if args.dry_run:
        print("当前为 --dry-run 预览模式，未执行任何上传")
    else:
        print(f"已上传     : {uploaded}")
        print(f"已存在跳过 : {skipped}")
        print(f"失败       : {len(failed)}")
        if failed:
            print("失败清单:")
            for u in failed:
                print("  -", u)
    print("\n下一步：执行 document/oss_migrate/update_image_urls.sql 替换数据库 URL 域名")


if __name__ == "__main__":
    main()
