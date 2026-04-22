"""
Classification metrics for clean, FGSM, and Byzantine evaluation conditions.

Writes per-dataset CSV rows and appends to a global summary comparison table.
"""

from __future__ import annotations

import csv
from pathlib import Path
from typing import Any, Dict

SUMMARY_COLUMNS = [
    "dataset",
    "condition",
    "extra",
    "compromised_agents",
    "accuracy",
    "precision_weighted",
    "recall_weighted",
    "f1_weighted",
    "fpr",
]

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns
from sklearn.metrics import (
    accuracy_score,
    f1_score,
    precision_score,
    recall_score,
)


def false_positive_rate(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    """
    FPR = FP / (FP + TN) for binary labels (0=normal, 1=attack).
    """
    y_true = np.asarray(y_true).astype(int)
    y_pred = np.asarray(y_pred).astype(int)
    tn = np.sum((y_true == 0) & (y_pred == 0))
    fp = np.sum((y_true == 0) & (y_pred == 1))
    denom = fp + tn
    if denom == 0:
        return 0.0
    return float(fp / denom)


def compute_metrics(y_true: np.ndarray, y_pred: np.ndarray) -> Dict[str, float]:
    """
    Return accuracy, weighted precision/recall/F1, and FPR.
    """
    y_true = np.asarray(y_true).astype(int)
    y_pred = np.asarray(y_pred).astype(int)
    return {
        "accuracy": float(accuracy_score(y_true, y_pred)),
        "precision_weighted": float(precision_score(y_true, y_pred, average="weighted", zero_division=0)),
        "recall_weighted": float(recall_score(y_true, y_pred, average="weighted", zero_division=0)),
        "f1_weighted": float(f1_score(y_true, y_pred, average="weighted", zero_division=0)),
        "fpr": false_positive_rate(y_true, y_pred),
    }


def print_metrics(title: str, m: Dict[str, float]) -> None:
    """Pretty-print one row of metrics."""
    print(f"\n--- {title} ---")
    print(f"  Accuracy           : {m['accuracy']:.4f}")
    print(f"  Precision (weighted): {m['precision_weighted']:.4f}")
    print(f"  Recall / DR (weighted): {m['recall_weighted']:.4f}")
    print(f"  F1 (weighted)      : {m['f1_weighted']:.4f}")
    print(f"  FPR                : {m['fpr']:.4f}")


METRICS_CSV_COLUMNS = [
    "dataset",
    "condition",
    "extra",
    "accuracy",
    "precision_weighted",
    "recall_weighted",
    "f1_weighted",
    "fpr",
]


def append_metrics_csv(
    results_dir: Path,
    dataset_name: str,
    condition: str,
    extra: str,
    metrics: Dict[str, float],
) -> None:
    """
    Append one result row to results/metrics_<dataset>.csv (create with header if new).
    """
    results_dir.mkdir(parents=True, exist_ok=True)
    path = results_dir / f"metrics_{dataset_name}.csv"
    row = {k: "" for k in METRICS_CSV_COLUMNS}
    row.update(
        {
            "dataset": dataset_name,
            "condition": condition,
            "extra": extra,
            **metrics,
        }
    )
    write_header = not path.is_file()
    with path.open("a", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=METRICS_CSV_COLUMNS)
        if write_header:
            w.writeheader()
        w.writerow(row)


def append_summary_row(results_dir: Path, row: Dict[str, Any]) -> None:
    """
    Append a row to results/summary_table.csv for cross-run comparison.

    All ``SUMMARY_COLUMNS`` are written; missing keys become empty strings.
    """
    results_dir.mkdir(parents=True, exist_ok=True)
    path = results_dir / "summary_table.csv"
    out = {k: row.get(k, "") for k in SUMMARY_COLUMNS}
    write_header = not path.is_file()
    with path.open("a", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=SUMMARY_COLUMNS)
        if write_header:
            w.writeheader()
        w.writerow(out)


def plot_metrics_comparison(
    df_summary: pd.DataFrame,
    dataset_name: str,
    results_dir: Path,
) -> None:
    """
    Save a bar plot comparing F1 under different conditions for one dataset.
    """
    if df_summary.empty:
        return
    sub = df_summary[df_summary["dataset"] == dataset_name]
    if sub.empty:
        return
    results_dir.mkdir(parents=True, exist_ok=True)
    try:
        plt.figure(figsize=(10, 5))
        sns.barplot(data=sub, x="condition", y="f1_weighted", hue="extra", dodge=True)
        plt.title(f"F1 (weighted) by condition — {dataset_name}")
        plt.xticks(rotation=25, ha="right")
        plt.tight_layout()
        out = results_dir / f"plot_f1_{dataset_name}.png"
        plt.savefig(out, dpi=150)
        plt.close()
        print(f"  Saved plot: {out}")
    except Exception as ex:
        plt.close("all")
        print(f"  (Skipping plot: {ex})")
