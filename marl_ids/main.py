"""
Entry point: load data, train CTDE MARL-IDPS, evaluate under clean / FGSM / Byzantine
conditions, save CSVs and plots under results/.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import torch

# Allow imports from marl_ids subfolders when running: python main.py
_ROOT = Path(__file__).resolve().parent
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from attacks.byzantine import predict_byzantine_batch
from attacks.fgsm import fgsm_generate_batch
from data.data_loader import load_dataset
from environment.network_env import NetworkEnv
from evaluation.evaluator import (
    append_metrics_csv,
    append_summary_row,
    compute_metrics,
    plot_metrics_comparison,
    print_metrics,
)
from training.ctde_trainer import predict_batch, train_ctde

SEED = 42
FGSM_EPSILONS = [0.05, 0.10, 0.20, 0.30]
BYZANTINE_LEVELS = [0.05, 0.10, 0.20]


def set_seeds(seed: int = SEED) -> None:
    """Fix NumPy and Torch RNGs for reproducibility."""
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def run_pipeline(dataset_key: str) -> None:
    """
    Execute the full experiment for one dataset name (nslkdd or unswnb15).
    """
    set_seeds(SEED)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")

    print(f"\n{'=' * 72}\nLoading dataset: {dataset_key}\n{'=' * 72}")
    try:
        X_train, X_test, y_train, y_test, feature_names = load_dataset(dataset_key)
    except FileNotFoundError as exc:
        print(f"\nAborting: {exc}\n")
        sys.exit(1)
    print(f"  Train shape: {X_train.shape}, Test shape: {X_test.shape}")
    print(f"  Features after preprocessing: {len(feature_names)}")

    env_ref = NetworkEnv(X_train, y_train)
    obs_dims = env_ref.observation_dims

    results_dir = _ROOT / "results"
    results_dir.mkdir(parents=True, exist_ok=True)
    slug = dataset_key.lower().replace("-", "")

    save_dir = _ROOT / "agents" / "saved_models"
    reward_plot_path = results_dir / f"{slug}_training_episode_rewards.png"
    agents = train_ctde(
        X_train,
        y_train,
        device=device,
        save_dir=save_dir,
        reward_plot_path=reward_plot_path,
    )

    rng = np.random.default_rng(SEED)

    # --- Clean baseline ---
    print(f"\n{'=' * 72}\nEvaluation: CLEAN ({dataset_key})\n{'=' * 72}")
    y_pred_clean = predict_batch(agents, X_test, obs_dims, explore=False)
    m_clean = compute_metrics(y_test, y_pred_clean)
    print_metrics("Clean (baseline)", m_clean)
    append_metrics_csv(results_dir, slug, "clean", "", m_clean)
    append_summary_row(
        results_dir,
        {"dataset": slug, "condition": "clean", "extra": "", "compromised_agents": "", **m_clean},
    )

    # --- FGSM ---
    print(f"\n{'=' * 72}\nEvaluation: FGSM ({dataset_key})\n{'=' * 72}")
    for eps in FGSM_EPSILONS:
        X_adv = fgsm_generate_batch(agents, X_test, y_test, eps, obs_dims, device)
        X_adv_np = X_adv.detach().cpu().numpy().astype(np.float32)
        y_pred_adv = predict_batch(agents, X_adv_np, obs_dims, explore=False)
        m = compute_metrics(y_test, y_pred_adv)
        print_metrics(f"FGSM epsilon={eps}", m)
        append_metrics_csv(results_dir, slug, "fgsm", str(eps), m)
        append_summary_row(
            results_dir,
            {"dataset": slug, "condition": "fgsm", "extra": str(eps), "compromised_agents": "", **m},
        )

    # --- Byzantine ---
    print(f"\n{'=' * 72}\nEvaluation: Byzantine ({dataset_key})\n{'=' * 72}")
    for level in BYZANTINE_LEVELS:
        y_pred_b, compromised = predict_byzantine_batch(
            agents, X_test, obs_dims, level, rng
        )
        m = compute_metrics(y_test, y_pred_b)
        print_metrics(f"Byzantine level={level} (compromised agents: {sorted(compromised)})", m)
        append_metrics_csv(results_dir, slug, "byzantine", str(level), m)
        append_summary_row(
            results_dir,
            {
                "dataset": slug,
                "condition": "byzantine",
                "extra": str(level),
                "compromised_agents": ",".join(str(i) for i in sorted(compromised)),
                **m,
            },
        )

    # Plot from accumulated summary
    if (results_dir / "summary_table.csv").is_file():
        df_sum = pd.read_csv(results_dir / "summary_table.csv")
        plot_metrics_comparison(df_sum, slug, results_dir)


def print_final_summary() -> None:
    """Load summary_table.csv and print a compact table to the console."""
    path = _ROOT / "results" / "summary_table.csv"
    if not path.is_file():
        print("\n(No summary_table.csv yet.)")
        return
    df = pd.read_csv(path)
    print("\n" + "=" * 72)
    print("FINAL SUMMARY (all appended runs)")
    print("=" * 72)
    with pd.option_context("display.max_rows", None, "display.width", 120):
        print(df.to_string(index=False))


def main() -> None:
    parser = argparse.ArgumentParser(description="MARL CTDE Intrusion Detection (5G)")
    parser.add_argument(
        "--dataset",
        type=str,
        required=True,
        choices=["nslkdd", "unswnb15", "all"],
        help="Dataset: nslkdd, unswnb15, or all (both sequentially)",
    )
    args = parser.parse_args()
    if args.dataset == "all":
        for key in ("nslkdd", "unswnb15"):
            run_pipeline(key)
    else:
        run_pipeline(args.dataset)
    print_final_summary()


if __name__ == "__main__":
    main()
