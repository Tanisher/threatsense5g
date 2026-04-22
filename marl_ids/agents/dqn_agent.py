"""
Deep Q-Network agent for one 5G slice (RAN, MEC, or Core).

Each agent maintains its own replay buffer and Q / target networks. Training uses
Huber or MSE TD loss against the target network, which is synchronised from the
policy network every N episodes by the CTDE trainer.
"""

from __future__ import annotations

import random
from collections import deque
from typing import Deque, Optional, Tuple

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim


class QNetwork(nn.Module):
    """
    Fully connected Q-network: state_dim -> 128 -> 64 -> 2 (Q-values for actions 0 and 1).
    """

    def __init__(self, state_dim: int) -> None:
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(state_dim, 128),
            nn.ReLU(),
            nn.Linear(128, 64),
            nn.ReLU(),
            nn.Linear(64, 2),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """Return Q(s, ·) for both actions, shape (batch, 2)."""
        return self.net(x)


class DQNAgent:
    """
    DQN with replay buffer, epsilon-greedy exploration, and periodic target updates.

    Hyperparameters match the research specification (Adam, gamma, batch size, etc.).
    """

    REPLAY_CAPACITY = 10_000
    BATCH_SIZE = 64
    LR = 0.001
    GAMMA = 0.99
    EPSILON_START = 1.0
    EPSILON_END = 0.01
    EPSILON_DECAY = 0.995

    def __init__(self, state_dim: int, device: torch.device) -> None:
        """
        Parameters
        ----------
        state_dim : int
            Length of this agent's local observation vector.
        device : torch.device
            CPU or CUDA for tensors.
        """
        self.state_dim = state_dim
        self.device = device
        self.policy_net = QNetwork(state_dim).to(device)
        self.target_net = QNetwork(state_dim).to(device)
        self.target_net.load_state_dict(self.policy_net.state_dict())
        self.target_net.eval()
        self.optimizer = optim.Adam(self.policy_net.parameters(), lr=self.LR)
        self._replay: Deque[Tuple[np.ndarray, int, float, np.ndarray, bool]] = deque(
            maxlen=self.REPLAY_CAPACITY
        )
        self.epsilon = self.EPSILON_START

    def select_action(self, state: np.ndarray, explore: bool = True) -> int:
        """
        Epsilon-greedy action selection from local state.

        Parameters
        ----------
        state : np.ndarray
            Local observation vector.
        explore : bool
            If False, always take greedy action (evaluation / decentralised execution).
        """
        if explore and random.random() < self.epsilon:
            return random.randint(0, 1)
        with torch.no_grad():
            s = torch.as_tensor(state, dtype=torch.float32, device=self.device).unsqueeze(0)
            q = self.policy_net(s)
            return int(torch.argmax(q, dim=1).item())

    def store_transition(
        self,
        state: np.ndarray,
        action: int,
        reward: float,
        next_state: np.ndarray,
        done: bool,
    ) -> None:
        """
        Append one transition (s, a, r, s', done) to this agent's replay memory.

        The reward is the team reward broadcast from the environment (CTDE training).
        """
        self._replay.append(
            (state.astype(np.float32), int(action), float(reward), next_state.astype(np.float32), bool(done))
        )

    def train_step(self) -> Optional[float]:
        """
        Sample a minibatch and perform one gradient step on the policy network.

        Returns
        -------
        float or None
            TD loss scalar if a step was taken; None if the buffer is too small.
        """
        if len(self._replay) < self.BATCH_SIZE:
            return None
        batch = random.sample(self._replay, self.BATCH_SIZE)
        s, a, r, s2, d = zip(*batch)
        s_t = torch.as_tensor(np.stack(s), dtype=torch.float32, device=self.device)
        a_t = torch.as_tensor(a, dtype=torch.int64, device=self.device)
        r_t = torch.as_tensor(r, dtype=torch.float32, device=self.device)
        s2_t = torch.as_tensor(np.stack(s2), dtype=torch.float32, device=self.device)
        d_t = torch.as_tensor(d, dtype=torch.float32, device=self.device)

        q_sa = self.policy_net(s_t).gather(1, a_t.unsqueeze(1)).squeeze(1)
        with torch.no_grad():
            max_next = self.target_net(s2_t).max(1)[0]
            target = r_t + self.GAMMA * max_next * (1.0 - d_t)
        loss = nn.functional.mse_loss(q_sa, target)

        self.optimizer.zero_grad()
        loss.backward()
        self.optimizer.step()
        return float(loss.item())

    def decay_epsilon(self) -> None:
        """Apply multiplicative epsilon decay and clamp to epsilon_end."""
        self.epsilon = max(self.EPSILON_END, self.epsilon * self.EPSILON_DECAY)

    def update_target_network(self) -> None:
        """Hard-copy policy weights into the target network."""
        self.target_net.load_state_dict(self.policy_net.state_dict())

    def save(self, path: str) -> None:
        """Persist policy network state dict to disk."""
        torch.save(self.policy_net.state_dict(), path)

    def load(self, path: str) -> None:
        """Load policy weights (and sync target net)."""
        self.policy_net.load_state_dict(torch.load(path, map_location=self.device))
        self.update_target_network()
