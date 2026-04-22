"""
Centralised Training with Decentralised Execution (CTDE) for three cooperative DQNs.

During training, all agents observe local slices, share the same team reward, and each
runs its own gradient step every environment step (centralised learning signal).
During evaluation, each agent only needs its local observation to select an action;
the team prediction is a majority vote (decentralised execution).
"""

from __future__ import annotations

from pathlib import Path
from typing import List, Optional, Tuple

import matplotlib.pyplot as plt
import numpy as np
import torch

from agents.dqn_agent import DQNAgent
from environment.network_env import NetworkEnv, local_observations, majority_vote

NUM_EPISODES = 150
TARGET_UPDATE_INTERVAL_EPISODES = 10
AGENT_NAMES = ("RAN", "MEC", "Core")


def _make_agents(obs_dims: Tuple[int, int, int], device: torch.device) -> List[DQNAgent]:
    """Construct three DQN agents with state dimensions matching the observation slices."""
    return [DQNAgent(d, device) for d in obs_dims]


def _save_episode_reward_plot(episode_rewards: List[float], out_path: Path) -> None:
    """Plot cumulative team reward per episode and write a PNG file."""
    episodes = np.arange(1, len(episode_rewards) + 1, dtype=np.int32)
    fig, ax = plt.subplots(figsize=(8, 4))
    ax.plot(episodes, episode_rewards, color="tab:blue", linewidth=1.2)
    ax.set_xlabel("Episode")
    ax.set_ylabel("Total reward (sum over steps)")
    ax.set_title("Training: cumulative reward per episode")
    ax.grid(True, alpha=0.3)
    fig.tight_layout()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out_path, dpi=150)
    plt.close(fig)


def train_ctde(
    X_train: np.ndarray,
    y_train: np.ndarray,
    device: torch.device,
    save_dir: Optional[Path] = None,
    reward_plot_path: Optional[Path] = None,
) -> List[DQNAgent]:
    """
    Run CTDE training for NUM_EPISODES full passes over the training set.

    Parameters
    ----------
    X_train, y_train : np.ndarray
        Training features and binary labels.
    device : torch.device
        Torch device for neural networks.
    save_dir : Path, optional
        If set, save agent checkpoints under agents/saved_models/.
    reward_plot_path : Path, optional
        If set, save a PNG plot of total reward vs episode here after training.

    Returns
    -------
    list of DQNAgent
        The three trained agents (RAN, MEC, Core).
    """
    env = NetworkEnv(X_train, y_train)
    d0, d1, d2 = env.observation_dims
    agents = _make_agents((d0, d1, d2), device)

    print(
        f"\n[CTDE] Training {NUM_EPISODES} episodes on {env.n_samples} samples | "
        f"obs dims RAN/MEC/Core = {d0}/{d1}/{d2}"
    )

    episode_rewards: List[float] = []

    for ep in range(1, NUM_EPISODES + 1):
        local_states = env.reset()
        done = False
        ep_reward = 0.0
        steps = 0

        while not done:
            actions = [agents[i].select_action(local_states[i], explore=True) for i in range(3)]
            next_local, reward, done = env.step(actions)
            ep_reward += reward
            steps += 1

            for i in range(3):
                agents[i].store_transition(
                    local_states[i],
                    actions[i],
                    reward,
                    next_local[i],
                    done,
                )
                agents[i].train_step()

            local_states = next_local

        for ag in agents:
            ag.decay_epsilon()

        if ep % TARGET_UPDATE_INTERVAL_EPISODES == 0:
            for ag in agents:
                ag.update_target_network()

        episode_rewards.append(ep_reward)
        eps = agents[0].epsilon
        print(
            f"  Episode {ep:3d}/{NUM_EPISODES} | steps={steps} | "
            f"total_reward={ep_reward:.2f} | epsilon={eps:.4f}"
        )

    if reward_plot_path is not None:
        _save_episode_reward_plot(episode_rewards, reward_plot_path)
        print(f"  Saved episode reward plot: {reward_plot_path}")

    if save_dir is not None:
        save_dir.mkdir(parents=True, exist_ok=True)
        for name, ag in zip(AGENT_NAMES, agents):
            path = save_dir / f"dqn_{name.lower()}.pt"
            ag.save(str(path))
            print(f"  Saved {path}")

    return agents


def predict_batch(
    agents: List[DQNAgent],
    X: np.ndarray,
    obs_dims: Tuple[int, int, int],
    explore: bool = False,
) -> np.ndarray:
    """
    Decentralised execution: each agent acts on its slice; majority vote is the label.

    Parameters
    ----------
    agents : list of DQNAgent
        Three trained agents.
    X : np.ndarray
        Feature matrix (N, F).
    obs_dims : tuple
        (d0, d1, d2) observation split sizes.
    explore : bool
        Passed to select_action (False for evaluation).

    Returns
    -------
    np.ndarray
        Predicted binary labels, shape (N,).
    """
    preds = []
    for row in X:
        loc = local_observations(row, obs_dims)
        actions = [agents[i].select_action(loc[i], explore=explore) for i in range(3)]
        preds.append(majority_vote(actions))
    return np.array(preds, dtype=np.int64)
