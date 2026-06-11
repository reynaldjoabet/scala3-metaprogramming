package example;
public class CircularDoublyLinkedList<T> {

    private static class Node<T> {
        T data;
        Node<T> prev;
        Node<T> next;

        Node(T data) {
            this.data = data;
        }
    }

    private Node<T> head;
    private int size;

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void addFirst(T data) {
        Node<T> node = new Node<>(data);
        if (head == null) {
            node.next = node;
            node.prev = node;
            head = node;
        } else {
            Node<T> tail = head.prev;
            node.next = head;
            node.prev = tail;
            tail.next = node;
            head.prev = node;
            head = node;
        }
        size++;
    }

    public void addLast(T data) {
        if (head == null) {
            addFirst(data);
            return;
        }
        Node<T> node = new Node<>(data);
        Node<T> tail = head.prev;
        node.next = head;
        node.prev = tail;
        tail.next = node;
        head.prev = node;
        size++;
    }

    public T removeFirst() {
        if (head == null) throw new java.util.NoSuchElementException("List is empty");
        T data = head.data;
        if (size == 1) {
            head = null;
        } else {
            Node<T> tail = head.prev;
            head = head.next;
            head.prev = tail;
            tail.next = head;
        }
        size--;
        return data;
    }

    public T removeLast() {
        if (head == null) throw new java.util.NoSuchElementException("List is empty");
        Node<T> tail = head.prev;
        T data = tail.data;
        if (size == 1) {
            head = null;
        } else {
            Node<T> newTail = tail.prev;
            newTail.next = head;
            head.prev = newTail;
        }
        size--;
        return data;
    }

    public T peekFirst() {
        if (head == null) throw new java.util.NoSuchElementException("List is empty");
        return head.data;
    }

    public T peekLast() {
        if (head == null) throw new java.util.NoSuchElementException("List is empty");
        return head.prev.data;
    }

    public boolean remove(T data) {
        if (head == null) return false;
        Node<T> current = head;
        for (int i = 0; i < size; i++) {
            if ((data == null && current.data == null) ||
                (data != null && data.equals(current.data))) {
                if (size == 1) {
                    head = null;
                } else {
                    current.prev.next = current.next;
                    current.next.prev = current.prev;
                    if (current == head) head = current.next;
                }
                size--;
                return true;
            }
            current = current.next;
        }
        return false;
    }

    public boolean contains(T data) {
        if (head == null) return false;
        Node<T> current = head;
        for (int i = 0; i < size; i++) {
            if ((data == null && current.data == null) ||
                (data != null && data.equals(current.data))) {
                return true;
            }
            current = current.next;
        }
        return false;
    }

    public void clear() {
        head = null;
        size = 0;
    }

    @Override
    public String toString() {
        if (head == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        Node<T> current = head;
        for (int i = 0; i < size; i++) {
            sb.append(current.data);
            if (i < size - 1) sb.append(" <-> ");
            current = current.next;
        }
        sb.append("] (circular)");
        return sb.toString();
    }
}
