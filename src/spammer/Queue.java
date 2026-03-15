package spammer;

class Queue<T> {
    private Object[] elements;
    private int head;
    private int tail;
    private int size;

    public Queue(int capacity) {
        elements = new Object[capacity];
        head = 0;
        tail = 0;
        size = 0;
    }

    public void enqueue(T item) {
        if (size == elements.length) {
            throw new IllegalStateException("Queue is full");
        }
        elements[tail] = item;
        tail = (tail + 1) % elements.length;
        size++;
    }

    @SuppressWarnings("unchecked")
    public T dequeue() {
        if (size == 0) {
            throw new IllegalStateException("Queue is empty");
        }
        T item = (T) elements[head];
        elements[head] = null; // Help GC
        head = (head + 1) % elements.length;
        size--;
        return item;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean isFull() {
        return size == elements.length;
    }

    public int getSize() {
        return size;
    }
}
