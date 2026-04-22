"""
Fast Gradient Sign Method (FGSM) evasion attack on the joint MARL policy.

Uses a differentiable surrogate: mean of per-agent softmax probabilities for the
"attack" action, trained implicitly through the Q-networks. Gradients are taken
w.r.t. the full feature vector so all three local observations receive consistent
perturbations, then clipped to [0, 1].
"""

from __future__ import annotations

from typing import List, Tuple

import torch
import torch.nn as nn

from agents.dqn_agent import DQNAgent


def _joint_attack_probability(
    agents: List[DQNAgent],
    x: torch.Tensor,
    obs_dims: Tuple[int, int, int],
) -> torch.Tensor:
    """
    Compute a differentiable scalar per sample in [0, 1]: average P(attack | local Q).

    Parameters
    ----------
    agents : list of DQNAgent
        Three DQNs (policy nets used for gradient flow).
    x : torch.Tensor
        Full feature batch, shape (B, F), requires_grad True.
    obs_dims : tuple
        (d0, d1, d2) slice sizes.

    Returns
    -------
    torch.Tensor
        Shape (B,) — mean softmax Q(action=1) across agents.
    """
    d0, d1, d2 = obs_dims
    chunks = (x[:, :d0], x[:, d0 : d0 + d1], x[:, d0 + d1 :])
    probs = []
    for i, chunk in enumerate(chunks):
        q = agents[i].policy_net(chunk)
        p_attack = torch.softmax(q, dim=1)[:, 1]
        probs.append(p_attack)
    return torch.stack(probs, dim=1).mean(dim=1)


def fgsm_perturb(
    agents: List[DQNAgent],
    X: torch.Tensor,
    y: torch.Tensor,
    epsilon: float,
    obs_dims: Tuple[int, int, int],
) -> torch.Tensor:
    """
    Apply FGSM: x_adv = clip(x + epsilon * sign(grad_x L), 0, 1).

    Loss L is binary cross-entropy between joint attack probability and true label.

    Parameters
    ----------
    agents : list of DQNAgent
        Trained agents (policy networks must be in train/eval mode; eval is fine for forward).
    X : torch.Tensor
        Features (B, F), will be cloned with requires_grad inside.
    y : torch.Tensor
        Binary labels (B,) as 0/1 float or long.
    epsilon : float
        L_inf perturbation budget.
    obs_dims : tuple
        Observation split for the three agents.

    Returns
    -------
    torch.Tensor
        Adversarial features, same shape as X, clipped to [0, 1].
    """
    device = X.device
    x_adv = X.detach().clone().to(device)
    x_adv.requires_grad_(True)

    # Gradients w.r.t. inputs only; freeze Q-network parameters for this surrogate step.
    param_grad_flags: List[bool] = []
    for ag in agents:
        for p in ag.policy_net.parameters():
            param_grad_flags.append(p.requires_grad)
            p.requires_grad_(False)

    prob = _joint_attack_probability(agents, x_adv, obs_dims).clamp(1e-7, 1.0 - 1e-7)
    y_float = y.float().view(-1)
    loss = nn.functional.binary_cross_entropy(prob, y_float)
    loss.backward()

    grad_sign = x_adv.grad.sign()

    idx = 0
    for ag in agents:
        for p in ag.policy_net.parameters():
            p.requires_grad_(param_grad_flags[idx])
            idx += 1
    x_pert = X.detach() + epsilon * grad_sign
    return torch.clamp(x_pert, 0.0, 1.0)


def fgsm_generate_batch(
    agents: List[DQNAgent],
    X_np,
    y_np,
    epsilon: float,
    obs_dims: Tuple[int, int, int],
    device: torch.device,
    batch_size: int = 512,
) -> torch.Tensor:
    """
    NumPy-friendly wrapper: tensor in/out on the given device for one epsilon value.

    Processes in mini-batches to limit GPU memory use on large test sets.
    """
    X_np = np.asarray(X_np, dtype=np.float32)
    y_np = np.asarray(y_np)
    chunks: List[torch.Tensor] = []
    for start in range(0, len(X_np), batch_size):
        end = start + batch_size
        X_t = torch.as_tensor(X_np[start:end], dtype=torch.float32, device=device)
        y_t = torch.as_tensor(y_np[start:end], dtype=torch.float32, device=device)
        chunks.append(fgsm_perturb(agents, X_t, y_t, epsilon, obs_dims))
    return torch.cat(chunks, dim=0)
