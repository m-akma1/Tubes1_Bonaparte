package lailatulCoder.util;

public class Stack<T> {
    private Object[] elements;
    public int size;

    public Stack(int capacity) {
        elements = new Object[capacity];
        size = 0;
    }

    public void push(T element) {
        if (element != null && size < elements.length) {
            elements[size++] = element;
        }
    }

    @SuppressWarnings("unchecked")
    public T pop() {
        if (size > 0) {
            return (T) elements[--size];
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public T peek() {
        if (size > 0) {
            return (T) elements[size - 1];
        }
        return null;
    }

    public boolean isEmpty() {
        return size == 0;
    }
    
    public void clear() {
        size = 0;
    }
}
