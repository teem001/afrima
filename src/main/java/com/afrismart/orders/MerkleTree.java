package com.afrismart.orders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Minimal Merkle tree over hex SHA-256 leaf hashes, for anchoring batches of order hashes. */
public final class MerkleTree {

    private MerkleTree() {
    }

    public static String root(List<String> leaves) {
        if (leaves.isEmpty()) {
            return sha256("");
        }
        List<String> level = new ArrayList<>(leaves);
        while (level.size() > 1) {
            level = nextLevel(level);
        }
        return level.getFirst();
    }

    /** Sibling hashes from leaf to root; each step notes whether the sibling sits on the left. */
    public static List<ProofStep> proof(List<String> leaves, int index) {
        List<ProofStep> steps = new ArrayList<>();
        List<String> level = new ArrayList<>(leaves);
        int idx = index;
        while (level.size() > 1) {
            boolean siblingOnLeft = idx % 2 == 1;
            int sibling = siblingOnLeft ? idx - 1 : Math.min(idx + 1, level.size() - 1);
            steps.add(new ProofStep(level.get(sibling), siblingOnLeft));
            level = nextLevel(level);
            idx /= 2;
        }
        return steps;
    }

    public static String verify(String leaf, List<ProofStep> proof) {
        String current = leaf;
        for (ProofStep step : proof) {
            current = step.siblingOnLeft() ? sha256(step.hash() + current) : sha256(current + step.hash());
        }
        return current;
    }

    private static List<String> nextLevel(List<String> level) {
        List<String> next = new ArrayList<>();
        for (int i = 0; i < level.size(); i += 2) {
            String left = level.get(i);
            String right = i + 1 < level.size() ? level.get(i + 1) : left;
            next.add(sha256(left + right));
        }
        return next;
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public record ProofStep(String hash, boolean siblingOnLeft) {
    }
}
