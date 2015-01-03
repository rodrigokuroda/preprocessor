package br.edu.utfpr.minerador.preprocessor.model;

import java.util.Objects;

/**
 *
 * @author Rodrigo T. Kuroda
 */
public class Commit {

    private final Integer id;
    private final String message;

    public Commit(Integer id, String message) {
        this.id = id;
        this.message = message;
    }

    public Integer getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.id);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Commit other = (Commit) obj;
        return Objects.equals(this.id, other.id);
    }

}
