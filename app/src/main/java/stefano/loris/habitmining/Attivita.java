package stefano.loris.habitmining;

public class Attivita {

    // mandatory stuff
    private String nome;
    private double probabilita;

    // optional stuff
    private String timestampStart;

    public Attivita(String nome, double probabilita) {
        this.nome = nome;
        this.probabilita = probabilita;
    }

    // MANDATORY GETTERS

    public String getNome() {
        return nome;
    }

    public double getProbabilita() {
        return probabilita;
    }

    // OPTIONAL GETTERS AND SETTERS

    public String getTimestampStart() {
        return timestampStart;
    }

    public void setTimestampStart(String timestampStart) {
        this.timestampStart = timestampStart;
    }

    public boolean equals(Object other) {
        if (other == null) return false;
        if (other == this) return true;
        if (!(other instanceof Attivita))return false;
        Attivita otherMyClass = (Attivita) other;

        return this.nome.equals(otherMyClass.getNome());
    }
}
