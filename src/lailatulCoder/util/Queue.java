package lailatulCoder.util;

public class Queue<T> {
    private Object[] elements;
    public int head;
    public int tail;
    public int size;

    public Queue(int capacity) {
        elements = new Object[capacity];
        head = 0;
        tail = 0;
        size = 0;
    }

    public void push(T element) {
        if (element != null && size < elements.length) {
            elements[tail] = element;
            tail = (tail + 1) % elements.length;
            size++;
        }
    }

    @SuppressWarnings("unchecked")
    public T pop() {
        if (size > 0) {
            T element = (T) elements[head];
            head = (head + 1) % elements.length;
            size--;
            return element;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public T peek() {
        if (size > 0) {
            return (T) elements[head];
        }
        return null;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean isFull() {
        return size == elements.length;
    }
    
    public void clear() {
        head = 0;
        tail = 0;
        size = 0;
    }
}
