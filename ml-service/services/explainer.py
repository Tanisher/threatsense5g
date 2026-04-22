from typing import Any, Dict, List

import numpy as np
import shap

from . import analyser


_explainer = None


def _get_explainer():
    global _explainer
    if _explainer is None and analyser.models_loaded():
        try:
            rf_model = analyser._rf_model  # type: ignore[attr-defined]
            _explainer = shap.TreeExplainer(rf_model)
        except Exception:
            _explainer = None
    return _explainer


FEATURE_NAMES = [
    'packet_size',
    'packet_count',
    'duration_ms',
    'protocol_encoded',
    'slice_type_encoded',
]


def explain(record: Dict[str, Any], threat_type: str) -> str:
    threat_type = (threat_type or 'NORMAL').upper()

    explainer = _get_explainer()
    if explainer is None:
        return _fallback_explanation(threat_type)

    try:
        features = np.array(
            [
                float(record.get('packet_size', 0.0)),
                float(record.get('packet_count', 0.0)),
                float(record.get('duration_ms', 0.0)),
                float(analyser._encode_protocol(record.get('protocol'))),  # type: ignore[attr-defined]
                float(analyser._encode_slice_type(record.get('slice_type'))),  # type: ignore[attr-defined]
            ],
            dtype=float,
        ).reshape(1, -1)

        rf_model = analyser._rf_model  # type: ignore[attr-defined]
        classes: List[str] = list(rf_model.classes_)
        target_index = classes.index(threat_type) if threat_type in classes else 0

        shap_values = explainer.shap_values(features)
        if isinstance(shap_values, list):
            sv = shap_values[target_index][0]
        else:
            sv = shap_values[0]

        contributions = list(zip(FEATURE_NAMES, sv, features[0]))
        contributions.sort(key=lambda item: abs(item[1]), reverse=True)
        top = contributions[:2]

        parts: List[str] = []
        for name, _, value in top:
            numeric_value = float(value)
            if numeric_value.is_integer():
                display_value = int(numeric_value)
            else:
                display_value = round(numeric_value, 2)
            parts.append(f"{name} ({display_value})")

        if not parts:
            return _fallback_explanation(threat_type)

        joined = ' and '.join(parts)
        pretty_type = threat_type.title()
        return f"Flagged due to {joined}, consistent with {pretty_type} pattern."
    except Exception:
        return _fallback_explanation(threat_type)


def _fallback_explanation(threat_type: str) -> str:
    if threat_type == 'DDOS':
        return 'High packet count and large packet size detected, consistent with DDoS flood pattern.'
    if threat_type == 'INTRUSION':
        return 'Unusual protocol and duration detected, consistent with intrusion attempt.'
    if threat_type == 'ANOMALY':
        return 'Traffic pattern deviates from normal baseline and is flagged as anomaly.'
    return 'Traffic analysed and classified as normal baseline behaviour.'

