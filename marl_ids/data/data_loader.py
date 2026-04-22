"""
Load and preprocess NSL-KDD and UNSW-NB15 for binary intrusion detection.

Combines official train/test files, applies the same preprocessing pipeline,
then performs a stratified 80/20 train/test split as required by the experiment spec.
"""

from __future__ import annotations

from pathlib import Path
from typing import List, Tuple

import numpy as np
import pandas as pd
from sklearn.feature_selection import VarianceThreshold
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import MinMaxScaler, OneHotEncoder

_SPLIT_RANDOM_STATE = 42


def _make_one_hot_encoder() -> OneHotEncoder:
    """
    Build a dense OneHotEncoder across scikit-learn versions.

    sklearn >= 1.2 uses ``sparse_output=False``; older releases use ``sparse=False``.
    """
    try:
        return OneHotEncoder(sparse_output=False, handle_unknown="ignore")
    except TypeError:
        return OneHotEncoder(sparse=False, handle_unknown="ignore")

_NSL_COLS_41 = [
    "duration",
    "protocol_type",
    "service",
    "flag",
    "src_bytes",
    "dst_bytes",
    "land",
    "wrong_fragment",
    "urgent",
    "hot",
    "num_failed_logins",
    "logged_in",
    "num_compromised",
    "root_shell",
    "su_attempted",
    "num_root",
    "num_file_creations",
    "num_shells",
    "num_access_files",
    "num_outbound_cmds",
    "is_host_login",
    "is_guest_login",
    "count",
    "srv_count",
    "serror_rate",
    "srv_serror_rate",
    "rerror_rate",
    "srv_rerror_rate",
    "same_srv_rate",
    "diff_srv_rate",
    "srv_diff_host_rate",
    "dst_host_count",
    "dst_host_srv_count",
    "dst_host_same_srv_rate",
    "dst_host_diff_srv_rate",
    "dst_host_same_src_port_rate",
    "dst_host_srv_diff_host_rate",
    "dst_host_serror_rate",
    "dst_host_srv_serror_rate",
    "dst_host_rerror_rate",
    "dst_host_srv_rerror_rate",
]


def _project_datasets_dir() -> Path:
    """Return the path to marl_ids/datasets (next to this package)."""
    return Path(__file__).resolve().parent.parent / "datasets"


def _check_datasets_folder_exists(datasets_dir: Path) -> None:
    """
    Verify the datasets directory exists; if not, print instructions and raise FileNotFoundError.
    """
    if datasets_dir.is_dir():
        return
    print("\n" + "=" * 72)
    print("DATASETS FOLDER NOT FOUND")
    print("=" * 72)
    print(f"Expected directory: {datasets_dir}")
    print("\nPlease create the folder and download the files:\n")
    print("  NSL-KDD (https://www.unb.ca/cic/datasets/nsl.html):")
    print("    - KDDTrain+.txt")
    print("    - KDDTest+.txt")
    print("  UNSW-NB15 (https://research.unsw.edu.au/projects/unsw-nb15-dataset):")
    print("    - UNSW_NB15_training-set.csv")
    print("    - UNSW_NB15_testing-set.csv")
    print("\nPlace all files directly inside the 'datasets' folder above, then re-run.\n")
    raise FileNotFoundError(f"Datasets directory missing: {datasets_dir}")


def _ensure_files_exist(datasets_dir: Path, required: List[str]) -> None:
    """Raise with a clear message if any required file is missing."""
    missing = [name for name in required if not (datasets_dir / name).is_file()]
    if not missing:
        return
    print("\nMissing dataset file(s) in", datasets_dir)
    for m in missing:
        print(f"  - {m}")
    print("\nDownload the files listed in the README for this project and place them in:")
    print(f"  {datasets_dir}\n")
    raise FileNotFoundError(f"Missing: {missing}")


def _load_nsl_kdd_combined(datasets_dir: Path) -> pd.DataFrame:
    """
    Load KDDTrain+.txt and KDDTest+.txt, concatenate into one DataFrame with consistent columns.
    """
    train_path = datasets_dir / "KDDTrain+.txt"
    test_path = datasets_dir / "KDDTest+.txt"
    df_tr = pd.read_csv(train_path, header=None, low_memory=False)
    df_te = pd.read_csv(test_path, header=None, low_memory=False)
    ncols = df_tr.shape[1]
    if ncols == 43:
        cols = _NSL_COLS_41 + ["difficulty", "label"]
    elif ncols == 42:
        cols = _NSL_COLS_41 + ["label"]
    else:
        cols = [f"f{i}" for i in range(ncols - 1)] + ["label"]
    df_tr.columns = cols[: df_tr.shape[1]]
    df_te.columns = cols[: df_te.shape[1]]
    df = pd.concat([df_tr, df_te], axis=0, ignore_index=True)
    if "difficulty" in df.columns:
        df = df.drop(columns=["difficulty"])
    return df


def _binary_label_nsl(series: pd.Series) -> np.ndarray:
    """Map NSL-KDD labels: normal -> 0, any attack -> 1."""
    s = series.astype(str).str.lower().str.strip()
    return np.where(s == "normal", 0, 1).astype(np.int64)


def _load_unsw_combined(datasets_dir: Path) -> pd.DataFrame:
    """Load UNSW training and testing CSVs and concatenate."""
    tr = datasets_dir / "UNSW_NB15_training-set.csv"
    te = datasets_dir / "UNSW_NB15_testing-set.csv"
    df_tr = pd.read_csv(tr, low_memory=False)
    df_te = pd.read_csv(te, low_memory=False)
    return pd.concat([df_tr, df_te], axis=0, ignore_index=True)


def _binary_label_unsw(df: pd.DataFrame) -> np.ndarray:
    """
    Build binary labels for UNSW-NB15 using the 'label' column (0/1) if present,
    else infer from 'attack_cat'.
    """
    if "label" in df.columns:
        return df["label"].astype(np.int64).values
    if "attack_cat" in df.columns:
        ac = df["attack_cat"].astype(str).str.lower().str.strip()
        return np.where(ac == "normal", 0, 1).astype(np.int64)
    raise ValueError("UNSW-NB15 dataframe must contain 'label' or 'attack_cat'.")


def _preprocess_frame(
    df: pd.DataFrame,
    cat_cols: List[str],
    y: np.ndarray,
) -> Tuple[np.ndarray, np.ndarray, List[str]]:
    """
    Impute missing values, one-hot encode categoricals, min-max scale numeric columns,
    apply variance threshold feature selection.

    Parameters
    ----------
    df : pd.DataFrame
        Feature columns only (no label).
    cat_cols : list of str
        Categorical column names present in df.
    y : np.ndarray
        Binary labels aligned with df rows.

    Returns
    -------
    X, y, feature_names
    """
    df = df.copy()
    df = df.replace(r"^\s*$", np.nan, regex=True)

    X_num = df.drop(columns=cat_cols, errors="ignore").copy()
    for c in X_num.columns:
        X_num[c] = pd.to_numeric(X_num[c], errors="coerce")
        med = X_num[c].median()
        if pd.isna(med):
            med = 0.0
        X_num[c] = X_num[c].fillna(med)

    X_cat = df[cat_cols].copy() if cat_cols else pd.DataFrame(index=df.index)
    for c in X_cat.columns:
        X_cat[c] = X_cat[c].fillna("missing").astype(str)

    scaler = MinMaxScaler()
    X_num_scaled = scaler.fit_transform(X_num.values).astype(np.float32)

    if cat_cols:
        ohe = _make_one_hot_encoder()
        X_cat_enc = ohe.fit_transform(X_cat.values).astype(np.float32)
        cat_names = list(ohe.get_feature_names_out(cat_cols))
    else:
        X_cat_enc = np.zeros((len(df), 0), dtype=np.float32)
        cat_names = []

    X = np.hstack([X_num_scaled, X_cat_enc]).astype(np.float32)
    num_names = [f"num__{c}" for c in X_num.columns]
    feature_names: List[str] = num_names + cat_names

    vt = VarianceThreshold(threshold=1e-6)
    X_sel = vt.fit_transform(X)
    support = vt.get_support(indices=True)
    feature_names = [feature_names[i] for i in support]

    return X_sel.astype(np.float32), y.astype(np.int64), feature_names


def load_nsl_kdd() -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, List[str]]:
    """
    Load NSL-KDD train+test files, preprocess, stratified 80/20 split.

    Returns
    -------
    X_train, X_test, y_train, y_test, feature_names
    """
    datasets_dir = _project_datasets_dir()
    _check_datasets_folder_exists(datasets_dir)
    _ensure_files_exist(datasets_dir, ["KDDTrain+.txt", "KDDTest+.txt"])
    df = _load_nsl_kdd_combined(datasets_dir)
    df = df.drop_duplicates().reset_index(drop=True)
    y = _binary_label_nsl(df["label"])
    df_x = df.drop(columns=["label"])
    cat_cols = [c for c in ["protocol_type", "service", "flag"] if c in df_x.columns]

    X, y_final, names = _preprocess_frame(df_x, cat_cols, y)
    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y_final,
        test_size=0.2,
        random_state=_SPLIT_RANDOM_STATE,
        stratify=y_final,
    )
    return X_train, X_test, y_train, y_test, names


def load_unsw_nb15() -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, List[str]]:
    """
    Load UNSW-NB15 training+testing CSVs, preprocess, stratified 80/20 split.

    Returns
    -------
    X_train, X_test, y_train, y_test, feature_names
    """
    datasets_dir = _project_datasets_dir()
    _check_datasets_folder_exists(datasets_dir)
    _ensure_files_exist(
        datasets_dir,
        ["UNSW_NB15_training-set.csv", "UNSW_NB15_testing-set.csv"],
    )
    df = _load_unsw_combined(datasets_dir)
    df = df.drop_duplicates().reset_index(drop=True)
    y = _binary_label_unsw(df)

    drop_cols = {"label", "attack_cat", "Label", "id"}
    df_x = df.drop(columns=[c for c in drop_cols if c in df.columns], errors="ignore")
    cat_cols = [c for c in ["proto", "service", "state"] if c in df_x.columns]

    X, y_final, names = _preprocess_frame(df_x, cat_cols, y)
    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y_final,
        test_size=0.2,
        random_state=_SPLIT_RANDOM_STATE,
        stratify=y_final,
    )
    return X_train, X_test, y_train, y_test, names


def load_dataset(name: str) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, List[str]]:
    """
    Dispatch loader by dataset name: 'nslkdd' or 'unswnb15'.

    Parameters
    ----------
    name : str
        One of 'nslkdd', 'unswnb15' (case-insensitive).
    """
    key = name.strip().lower()
    if key in ("nslkdd", "nsl-kdd", "nsl_kdd"):
        return load_nsl_kdd()
    if key in ("unswnb15", "unsw-nb15", "unsw_nb15"):
        return load_unsw_nb15()
    raise ValueError(f"Unknown dataset: {name}. Use 'nslkdd' or 'unswnb15'.")
