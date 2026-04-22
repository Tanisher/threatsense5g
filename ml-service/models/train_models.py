import os
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest, RandomForestClassifier
from sklearn.metrics import accuracy_score, precision_recall_fscore_support
from sklearn.model_selection import train_test_split


RANDOM_STATE = 42


def generate_synthetic_traffic(n_rows: int = 10_000) -> pd.DataFrame:
    """
    Generate synthetic 5G-like network traffic with three classes:
    NORMAL, DDOS, INTRUSION.
    """
    rng = np.random.default_rng(RANDOM_STATE)

    labels = rng.choice(
        ["NORMAL", "DDOS", "INTRUSION"],
        size=n_rows,
        p=[0.8, 0.1, 0.1],
    )

    packet_size = np.zeros(n_rows)
    packet_count = np.zeros(n_rows)
    duration_ms = np.zeros(n_rows)
    protocol_encoded = np.zeros(n_rows, dtype=int)
    slice_type_encoded = np.zeros(n_rows, dtype=int)

    for i, lbl in enumerate(labels):
        if lbl == "NORMAL":
            packet_size[i] = rng.normal(600, 150)
            packet_count[i] = rng.normal(40, 10)
            duration_ms[i] = rng.normal(200, 60)
            protocol_encoded[i] = rng.choice([0, 1])  # TCP/UDP
            slice_type_encoded[i] = rng.choice([0, 1, 2])  # eMBB/URLLC/mMTC
        elif lbl == "DDOS":
            packet_size[i] = rng.normal(1_200, 250)  # larger packets
            packet_count[i] = rng.normal(400, 80)    # many packets
            duration_ms[i] = rng.normal(60, 20)      # short bursts
            protocol_encoded[i] = 0  # predominantly TCP
            slice_type_encoded[i] = rng.choice([0, 1])
        else:  # INTRUSION
            packet_size[i] = rng.normal(800, 200)
            packet_count[i] = rng.normal(120, 40)
            duration_ms[i] = rng.normal(600, 200)    # unusual long/short durations
            protocol_encoded[i] = rng.choice([1, 2, 3])  # UDP/ICMP/other
            slice_type_encoded[i] = rng.choice([1, 2])

    df = pd.DataFrame(
        {
            "packet_size": np.clip(packet_size, 64, 9_000),
            "packet_count": np.clip(packet_count, 1, 2_000).astype(int),
            "duration_ms": np.clip(duration_ms, 1, 10_000).astype(int),
            "protocol_encoded": protocol_encoded,
            "slice_type_encoded": slice_type_encoded,
            "label": labels,
        }
    )
    return df


def train_and_save_models():
    df = generate_synthetic_traffic()

    feature_cols = [
        "packet_size",
        "packet_count",
        "duration_ms",
        "protocol_encoded",
        "slice_type_encoded",
    ]
    X = df[feature_cols].values
    y = df["label"].values

    # Isolation Forest for anomaly detection
    iso = IsolationForest(
        n_estimators=200,
        contamination=0.2,
        random_state=RANDOM_STATE,
    )
    iso.fit(X)

    # Random Forest classifier
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=RANDOM_STATE, stratify=y
    )

    rf = RandomForestClassifier(
        n_estimators=300,
        max_depth=None,
        random_state=RANDOM_STATE,
        n_jobs=-1,
    )
    rf.fit(X_train, y_train)

    y_pred = rf.predict(X_test)
    acc = accuracy_score(y_test, y_pred)
    precision, recall, f1, _ = precision_recall_fscore_support(
        y_test, y_pred, average="weighted", zero_division=0
    )

    print("Random Forest performance on synthetic data:")
    print(f"  Accuracy : {acc:.4f}")
    print(f"  Precision: {precision:.4f}")
    print(f"  Recall   : {recall:.4f}")
    print(f"  F1-score : {f1:.4f}")

    models_dir = Path(__file__).resolve().parent
    os.makedirs(models_dir, exist_ok=True)

    iso_path = models_dir / "isolation_forest.pkl"
    rf_path = models_dir / "random_forest.pkl"

    joblib.dump(iso, iso_path)
    joblib.dump(rf, rf_path)

    print(f"Saved Isolation Forest to {iso_path}")
    print(f"Saved Random Forest to {rf_path}")


if __name__ == "__main__":
    train_and_save_models()

