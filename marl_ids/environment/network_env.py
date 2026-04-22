"""
Gym-style environment wrapping preprocessed traffic rows for multi-agent DQN training.

Each agent observes a contiguous slice of the global feature vector (RAN / MEC / Core).
The joint decision is a majority vote over three binary actions. A shared scalar reward
is computed from the vote and the ground-truth label using the asymmetric reward table.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import List, Tuple

import numpy as np


def _split_observation_dim(n_features: int) -> Tuple[int, int, int]:
    """
    Split total feature count into three as-equal-as-possible parts for RAN, MEC, Core.

    Returns
    -------
    d0, d1, d2
        Sizes for agents 0, 1, 2 (sum equals n_features).
    """
    base = n_features // 3
    rem = n_features % 3
    d0 = base + (1 if rem > 0 else 0)
    d1 = base + (1 if rem > 1 else 0)
    d2 = n_features - d0 - d1
    return d0, d1, d2


def local_observations(full_state: np.ndarray, dims: Tuple[int, int, int]) -> List[np.ndarray]:
    """
    Split a 1D feature vector into three local observation vectors.

    Parameters
    ----------
    full_state : np.ndarray
        Shape (F,) — one traffic record.
    dims : tuple
        (d_ran, d_mec, d_core) from _split_observation_dim.

    Returns
    -------
    list of 3 arrays
        Local states for agents 0, 1, 2.
    """
    d0, d1, d2 = dims
    s0 = full_state[:d0].astype(np.float32, copy=False)
    s1 = full_state[d0 : d0 + d1].astype(np.float32, copy=False)
    s2 = full_state[d0 + d1 :].astype(np.float32, copy=False)
    return [s0, s1, s2]


def majority_vote(actions: List[int]) -> int:
    """
    Return 1 if at least two agents choose attack (1), else 0 (normal).
    """
    return 1 if sum(int(a) for a in actions) >= 2 else 0


def compute_reward(true_label: int, prediction: int) -> float:
    """
    Shared reward for the joint decision (asymmetric cost table).

    true_label : 0 normal, 1 attack
    prediction : 0 normal, 1 attack (majority vote)
    """
    if true_label == 1 and prediction == 1:
        return 1.0
    if true_label == 1 and prediction == 0:
        return -1.0
    if true_label == 0 and prediction == 1:
        return -0.5
    return 0.2


@dataclass
class NetworkEnv:
    """
    Iterator over a dataset (X, y). One episode = one sequential pass over all rows.

    Attributes
    ----------
    X : np.ndarray
        Feature matrix, shape (N, F).
    y : np.ndarray
        Binary labels, shape (N,).
    """

    X: np.ndarray
    y: np.ndarray

    def __post_init__(self) -> None:
        if self.X.ndim != 2:
            raise ValueError("X must be 2D (N, features).")
        n = self.X.shape[0]
        if self.y.shape[0] != n:
            raise ValueError("X and y must have the same number of rows.")
        self._dims = _split_observation_dim(self.X.shape[1])
        self._idx: int = 0

    @property
    def observation_dims(self) -> Tuple[int, int, int]:
        """Per-agent input sizes for the three DQNs."""
        return self._dims

    def reset(self) -> List[np.ndarray]:
        """
        Start a new episode at the first sample; return local observations for all agents.
        """
        self._idx = 0
        return local_observations(self.X[self._idx], self._dims)

    def step(self, actions: List[int]) -> Tuple[List[np.ndarray], float, bool]:
        """
        Apply joint actions for the current index, emit reward and next observations.

        Parameters
        ----------
        actions : list of 3 int
            Binary action per agent (0=normal, 1=attack).

        Returns
        -------
        next_local_states : list of np.ndarray
            Observations at the next time index (or last state if done).
        reward : float
            Shared scalar reward.
        done : bool
            True after processing the last row in this episode.
        """
        true_label = int(self.y[self._idx])
        pred = majority_vote(actions)
        reward = compute_reward(true_label, pred)

        self._idx += 1
        done = self._idx >= len(self.X)
        if done:
            next_states = local_observations(self.X[-1], self._dims)
        else:
            next_states = local_observations(self.X[self._idx], self._dims)
        return next_states, reward, done

    def current_label(self) -> int:
        """Ground-truth label at the current time index (for evaluation / logging)."""
        return int(self.y[self._idx])

    @property
    def n_samples(self) -> int:
        return len(self.X)
