"""
Byzantine-style poisoning: random agents are compromised with noisy observations
and inverted greedy actions during evaluation.
"""

from __future__ import annotations

from typing import List, Set, Tuple

import numpy as np

from agents.dqn_agent import DQNAgent
from environment.network_env import local_observations, majority_vote


def predict_byzantine_batch(
    agents: List[DQNAgent],
    X: np.ndarray,
    obs_dims: Tuple[int, int, int],
    poisoning_fraction: float,
    rng: np.random.Generator,
    noise_std: float = 0.3,
) -> Tuple[np.ndarray, Set[int]]:
    """
    Evaluate under Byzantine poisoning.

    Each agent is marked compromised independently with probability ``poisoning_fraction``
    (interpreted as the poisoning level from the paper). If no agent is drawn but the
    level is positive, one random agent is compromised so the effect is visible.

    Compromised agents receive Gaussian noise (std ``noise_std``) on their local
    observation (clipped to [0, 1]) and use an inverted greedy action (1 - a).

    Parameters
    ----------
    agents : list of DQNAgent
        Three DQNs.
    X : np.ndarray
        Clean feature matrix (N, F).
    obs_dims : tuple
        Local observation sizes.
    poisoning_fraction : float
        Probability each agent is Byzantine (e.g. 0.05, 0.10, 0.20).
    rng : np.random.Generator
        Random source for agent selection and Gaussian noise.
    noise_std : float
        Standard deviation of Gaussian noise on compromised agents' observations.

    Returns
    -------
    predictions : np.ndarray
        Binary predictions after majority vote, shape (N,).
    compromised : set of int
        Agent indices that were compromised for this run (fixed for whole batch).
    """
    n_agents = len(agents)
    compromised_mask = rng.random(n_agents) < poisoning_fraction
    if poisoning_fraction > 0 and not compromised_mask.any():
        compromised_mask[rng.integers(0, n_agents)] = True
    compromised = set(int(i) for i in np.where(compromised_mask)[0])

    preds = []
    for row in X:
        loc = local_observations(row, obs_dims)
        actions = []
        for i in range(n_agents):
            obs = loc[i].astype(np.float32, copy=True)
            if i in compromised:
                obs = obs + rng.normal(0.0, noise_std, size=obs.shape).astype(np.float32)
                obs = np.clip(obs, 0.0, 1.0)
            greedy = agents[i].select_action(obs, explore=False)
            if i in compromised:
                greedy = 1 - greedy
            actions.append(greedy)
        preds.append(majority_vote(actions))
    return np.array(preds, dtype=np.int64), compromised
