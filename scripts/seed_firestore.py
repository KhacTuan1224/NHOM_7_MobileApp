"""Seed sample RescueFarm catalog and home content into Firestore.

Run once after installing google-cloud-firestore and authenticating with ADC:
  gcloud auth application-default login --project=nhom7-rescuefarm
  python -m pip install google-cloud-firestore
  python scripts/seed_firestore.py

The script uses deterministic document IDs and upserts only the sample documents
listed below. It never deletes data. Pass --dry-run to inspect the planned IDs.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
from typing import Any


PROJECT_ID = "nhom7-rescuefarm"
SELLER_ID = "mock-seller-rescuefarm"

# Stable image URLs are used so Glide can render product, category, and banner
# imagery without adding binary assets to the Android project.
IMAGE = {
    "fruit": "https://images.unsplash.com/photo-1490474418585-ba9bad8fd0ea?auto=format&fit=crop&w=1000&q=85",
    "apple": "https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?auto=format&fit=crop&w=1000&q=85",
    "vegetable": "https://images.unsplash.com/photo-1540420773420-3366772f4999?auto=format&fit=crop&w=1000&q=85",
    "leafy": "https://images.unsplash.com/photo-1576045057995-568f588f82fb?auto=format&fit=crop&w=1000&q=85",
    "rice": "https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=1000&q=85",
    "banner": "https://images.unsplash.com/photo-1470252649378-9c29740c9fa8?auto=format&fit=crop&w=1400&q=85",
    "banner2": "https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1400&q=85",
}


def build_documents(now: datetime) -> dict[str, dict[str, dict[str, Any]]]:
    today = now.replace(hour=0, minute=0, second=0, microsecond=0)
    categories = {
        "cat-fruit": {"name": "Trái cây", "imageUrl": IMAGE["fruit"], "active": True, "displayOrder": 1},
        "cat-vegetables": {"name": "Rau xanh", "imageUrl": IMAGE["vegetable"], "active": True, "displayOrder": 2},
        "cat-grains": {"name": "Củ & Hạt", "imageUrl": IMAGE["rice"], "active": True, "displayOrder": 3},
        "cat-rice": {"name": "Hạt gạo", "imageUrl": IMAGE["rice"], "active": True, "displayOrder": 4},
        "cat-herbs": {"name": "Rau gia vị", "imageUrl": IMAGE["leafy"], "active": True, "displayOrder": 5},
    }

    product_specs = [
        ("prod-binh-thuan-dragon-fruit", "cat-fruit", "Thanh long ruột đỏ Bình Thuận", 32000, 20000, "kg", "Vườn Minh Phát", "Bình Thuận", IMAGE["fruit"]),
        ("prod-da-lat-avocado", "cat-fruit", "Bơ sáp Đà Lạt", 55000, 39000, "kg", "Nông trại Xanh", "Lâm Đồng", IMAGE["fruit"]),
        ("prod-ripe-mango", "cat-fruit", "Xoài chín cây", 42000, 28000, "kg", "Vườn miền Tây", "Đồng Tháp", IMAGE["apple"]),
        ("prod-organic-spinach", "cat-vegetables", "Rau chân vịt tươi", 26000, 18000, "bó", "Hợp tác xã Rau Sạch", "Lâm Đồng", IMAGE["leafy"]),
        ("prod-fresh-tomato", "cat-vegetables", "Cà chua chín tự nhiên", 30000, 21000, "kg", "Nông trại Xanh", "Lâm Đồng", IMAGE["vegetable"]),
        ("prod-sweet-potato", "cat-grains", "Khoai lang mật", 28000, 19000, "kg", "Vườn miền Tây", "Vĩnh Long", IMAGE["vegetable"]),
        ("prod-st25-rice", "cat-rice", "Gạo thơm ST25", 38000, 32000, "kg", "Hợp tác xã Lúa Vàng", "Sóc Trăng", IMAGE["rice"]),
        ("prod-fresh-herbs", "cat-herbs", "Rau thơm hỗn hợp", 18000, 12000, "bó", "Hợp tác xã Rau Sạch", "TP. Hồ Chí Minh", IMAGE["leafy"]),
    ]
    products: dict[str, dict[str, Any]] = {}
    for product_id, category_id, name, original, rescue, unit, origin, province, image in product_specs:
        products[product_id] = {
            "id": product_id,
            "sellerId": SELLER_ID,
            "categoryId": category_id,
            "name": name,
            "description": f"{name} được thu hoạch từ {origin}, đang có giá giải cứu để hạn chế nông sản bị lãng phí.",
            "originalPrice": original,
            "rescuePrice": rescue,
            "unit": unit,
            "origin": origin,
            "province": province,
            "imageUrls": [image],
            "averageRating": 4.8,
            "reviewCount": 12,
            "status": "ACTIVE",
            "createdAt": now,
            "updatedAt": now,
        }

    # Batches and campaigns are linked by IDs so campaign detail and inventory
    # lookups can resolve the same mock products.
    batch_specs = [
        ("batch-dragon-fruit", "prod-binh-thuan-dragon-fruit", 80, 12),
        ("batch-avocado", "prod-da-lat-avocado", 60, 15),
        ("batch-spinach", "prod-organic-spinach", 45, 8),
        ("batch-sweet-potato", "prod-sweet-potato", 70, 10),
    ]
    batches: dict[str, dict[str, Any]] = {}
    for batch_id, product_id, quantity, days_to_expiry in batch_specs:
        batches[batch_id] = {
            "id": batch_id,
            "productId": product_id,
            "activeCampaignId": None,
            "harvestDate": today - timedelta(days=1),
            "expiryDate": today + timedelta(days=days_to_expiry),
            "initialQuantity": quantity,
            "availableQuantity": quantity,
            "reservedQuantity": 0,
            "soldQuantity": 0,
            "status": "AVAILABLE",
            "inventoryVersion": 1,
            "createdAt": now,
            "updatedAt": now,
        }

    campaign_specs = [
        ("campaign-critical-dragon-fruit", "batch-dragon-fruit", "Giải cứu thanh long ruột đỏ Bình Thuận", "NEAR_EXPIRY", "CRITICAL", "MOBILE_POINT", 21.0285, 105.8542, 7),
        ("campaign-mobile-avocado", "batch-avocado", "Điểm giải cứu bơ sáp Đà Lạt", "SEASONAL_HARVEST", "HIGH", "MOBILE_POINT", 10.7769, 106.7009, 8),
        ("campaign-fixed-spinach", "batch-spinach", "Rau xanh giải cứu tại điểm thu gom", "OVER_SUPPLY", "HIGH", "FIXED_POINT", 10.8231, 106.6297, 12),
        ("campaign-ending-sweet-potato", "batch-sweet-potato", "Khoai lang mật cuối vụ", "SEASONAL_HARVEST", "NORMAL", "FIXED_POINT", 10.0452, 105.7469, 1),
    ]
    campaigns: dict[str, dict[str, Any]] = {}
    for campaign_id, batch_id, title, reason, urgency, mode, latitude, longitude, end_days in campaign_specs:
        quantity = batches[batch_id]["initialQuantity"]
        mobile = mode == "MOBILE_POINT"
        batches[batch_id]["activeCampaignId"] = campaign_id
        campaigns[campaign_id] = {
            "id": campaign_id,
            "sellerId": SELLER_ID,
            "title": title,
            "description": "Nông sản còn tốt, cần kết nối người mua để thu hoạch và phân phối kịp thời.",
            "rescueReason": reason,
            "urgencyLevel": urgency,
            "rescueMode": mode,
            "batchTargets": {batch_id: quantity},
            "targetQuantity": quantity,
            "reservedQuantity": 0,
            "rescuedQuantity": 0,
            "startDate": now - timedelta(days=1),
            "endDate": now + timedelta(days=end_days),
            "latitude": latitude,
            "longitude": longitude,
            "currentLatitude": latitude,
            "currentLongitude": longitude,
            "locationUpdatedAt": now if mobile else None,
            "locationSharingEnabled": mobile,
            "locationName": "Điểm thu gom RescueFarm",
            "status": "ACTIVE",
            "createdAt": now,
            "updatedAt": now,
        }

    banners = {
        "banner-seed-rescue": {
            "id": "banner-seed-rescue",
            "title": "Cùng RescueFarm giải cứu nông sản Việt",
            "imageUrl": IMAGE["banner"],
            "campaignId": "campaign-critical-dragon-fruit",
            "displayOrder": 1,
            "startDate": now - timedelta(days=1),
            "endDate": now + timedelta(days=30),
            "active": True,
        },
        "banner-seed-fresh": {
            "id": "banner-seed-fresh",
            "title": "Nông sản tươi ngon, giá tốt mỗi ngày",
            "imageUrl": IMAGE["banner2"],
            "campaignId": "campaign-mobile-avocado",
            "displayOrder": 2,
            "startDate": now - timedelta(days=1),
            "endDate": now + timedelta(days=30),
            "active": True,
        },
    }

    promotions = {
        "prod-binh-thuan-dragon-fruit": {
            "id": "prod-binh-thuan-dragon-fruit",
            "sellerId": SELLER_ID,
            "productId": "prod-binh-thuan-dragon-fruit",
            "type": "VOLUME",
            "value": 0,
            "quantityDiscountTiers": {"5": 5, "10": 10},
            "startDate": now - timedelta(days=1),
            "endDate": now + timedelta(days=30),
            "active": True,
        },
        "prod-da-lat-avocado": {
            "id": "prod-da-lat-avocado",
            "sellerId": SELLER_ID,
            "productId": "prod-da-lat-avocado",
            "type": "VOLUME",
            "value": 0,
            "quantityDiscountTiers": {"3": 4, "6": 8},
            "startDate": now - timedelta(days=1),
            "endDate": now + timedelta(days=30),
            "active": True,
        },
    }
    return {
        "categories": categories,
        "products": products,
        "productBatches": batches,
        "campaigns": campaigns,
        "banners": banners,
        "promotions": promotions,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Import RescueFarm mock data into Firestore.")
    parser.add_argument("--project", default=PROJECT_ID, help=f"Firebase project ID (default: {PROJECT_ID})")
    parser.add_argument("--dry-run", action="store_true", help="Print planned document IDs without writing")
    parser.add_argument("--only", choices=["categories", "products", "productBatches", "campaigns", "banners", "promotions"], help="Upsert only one collection")
    args = parser.parse_args()

    documents = build_documents(datetime.now(timezone.utc))
    if args.only:
        documents = {args.only: documents[args.only]}
    print(f"Firestore project: {args.project}")
    print("Documents to upsert:")
    for collection, entries in documents.items():
        print(f"  {collection}: {len(entries)} ({', '.join(entries.keys())})")
    if args.dry_run:
        print("Dry run only; no Firestore documents were changed.")
        return 0

    try:
        from google.cloud import firestore
    except ImportError:
        print("Missing dependency. Install with: python -m pip install google-cloud-firestore")
        return 2

    try:
        db = firestore.Client(project=args.project)
        batch = db.batch()
        writes = 0
        for collection, entries in documents.items():
            for document_id, data in entries.items():
                batch.set(db.collection(collection).document(document_id), data)
                writes += 1
                if writes == 450:
                    batch.commit()
                    batch = db.batch()
                    writes = 0
        if writes:
            batch.commit()
    except Exception as error:  # Includes missing ADC and Firestore IAM errors.
        print(f"Import failed: {error}")
        print("Check ADC login, project ID, Firestore database availability, and IAM write access.")
        return 1

    total = sum(len(entries) for entries in documents.values())
    print(f"Done: upserted {total} mock documents. Existing documents outside these IDs were untouched.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
