from typing import Tuple


def score(threat_type: str, confidence: float) -> Tuple[str, float]:
    """
    Return (severity, adjusted_confidence) given a threat_type and base confidence.
    """
    threat_type = (threat_type or "NORMAL").upper()
    confidence = float(confidence or 0.0)

    if threat_type == "NORMAL":
        return "LOW", confidence

    if threat_type == "DDOS":
        if confidence > 0.85:
            return "CRITICAL", confidence
        if confidence > 0.65:
            return "HIGH", confidence
        return "MEDIUM", confidence

    if threat_type == "INTRUSION":
        if confidence > 0.80:
            return "HIGH", confidence
        return "MEDIUM", confidence

    # ANOMALY or anything else
    return "MEDIUM", confidence

