package net.creeperhost.creeperlauncher.util;

public class Pair<L, R>
{
    private final L left;
    private final R right;

    public Pair(L left, R right)
    {
        this.left = left;
        this.right = right;
    }

    L getLeft()
    {
        return left;
    }

    R getRight()
    {
        return right;
    }
}
