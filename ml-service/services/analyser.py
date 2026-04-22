import os
from pathlib import Path
from typing import Any, Dict, List

import joblib
import numpy as np

BASE_DIR = Path(__file__).resolve().parent.parent
MODELS_DIR = BASE_DIR / "models"

_iso_model = None
_rf_model = None


def _load_models() -> None:
    global _iso_model, _rf_model
    try:
        iso_path = MODELS_DIR / "isolation_forest.pkl"
        rf_path = MODELS_DIR / "random_forest.pkl"

        if not iso_path.exists() or not rf_path.exists():
            return

        _iso_model = joblib.load(iso_path)
        _rf_model = joblib.load(rf_path)
    except Exception:
        _iso_model = None
        _rf_model = None


_load_models()


def models_loaded() -> bool:
    return _iso_model is not None and _rf_model is not None


PROTOCOL_ENCODING = {"TCP": 0, "UDP": 1, "ICMP": 2}
SLICE_ENCODING = {"eMBB": 0, "URLLC": 1, "mMTC": 2}


def _encode_protocol(value: Any) -> int:
    if value is None:
        return 3
    key = str(value).upper()
    return PROTOCOL_ENCODING.get(key, 3)


def _encode_slice_type(value: Any) -> int:
    if value is None:
        return 0
    key = str(value)
    return SLICE_ENCODING.get(key, 0)


def _extract_features(record: Dict[str, Any]) -> np.ndarray:
    packet_size = float(record.get("packet_size", 0.0))
    packet_count = float(record.get("packet_count", 0.0))
    duration_ms = float(record.get("duration_ms", 0.0))
    protocol_encoded = _encode_protocol(record.get("protocol"))
    slice_type_encoded = _encode_slice_type(record.get("slice_type"))

    return np.array(
        [packet_size, packet_count, duration_ms, protocol_encoded, slice_type_encoded],
        dtype=float,
    )


def analyse(records: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    if not models_loaded():
        raise RuntimeError("Models are not loaded")

    if not records:
        return []

    X = np.vstack([_extract_features(r) for r in records])

    # Isolation Forest anomaly scores: -1 for anomaly, 1 for normal
    iso_preds = _iso_model.predict(X)

    # Random Forest predictions and probabilities
    rf_labels = _rf_model.predict(X)
    if hasattr(_rf_model, "predict_proba"):
        proba = _rf_model.predict_proba(X)
    else:
        proba = None

    results: List[Dict[str, Any]] = []
    classes = list(_rf_model.classes_)

    for i, rec in enumerate(records):
        rf_label = str(rf_labels[i])
        confidence = 0.0
        if proba is not None:
            label_index = classes.index(rf_label)
            confidence = float(proba[i][label_index])

        is_anomaly = iso_preds[i] == -1

        if rf_label == "NORMAL" and is_anomaly:
            threat_type = "ANOMALY"
        else:
            threat_type = rf_label

        results.append(
            {
                "threat_type": threat_type,
                "base_label": rf_label,
                "confidence_score": confidence,
                "is_anomaly": bool(is_anomaly),
            }
        )

    return results

