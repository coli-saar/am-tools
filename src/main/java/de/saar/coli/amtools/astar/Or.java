package de.saar.coli.amtools.astar;

/**
 * A disjunctive type which can hold values of either type E or F.
 * Use the isLeft method to find out which type it holds.
 *
 * @param <E>
 * @param <F>
 */
public class Or<E,F> {
    private E leftValue;
    private F rightValue;
    private boolean isLeft;

    public static <E,F> Or<E,F> createLeft(E e) {
        Or<E,F> ret = new Or();
        ret.leftValue = e;
        ret.isLeft = true;
        return ret;
    }

    public static <E,F> Or<E,F> createRight(F f) {
        Or<E,F> ret = new Or();
        ret.rightValue = f;
        ret.isLeft = false;
        return ret;
    }

    public E getLeftValue() {
        return leftValue;
    }

    public F getRightValue() {
        return rightValue;
    }

    public boolean isLeft() {
        return isLeft;
    }

    @Override
    public String toString() {
        if( isLeft ) {
            return String.format("Or(L,%s)", leftValue);
        } else {
            return String.format("Or(R,%s)", rightValue);
        }
    }
}
