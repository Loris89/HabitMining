package stefano.loris.habitmining;

public class Attivita {

    private String nome;
    private double probabilita;

    public Attivita(String nome, double probabilita) {
        this.nome = nome;
        this.probabilita = probabilita;
    }

    public String getNome() {
        return nome;
    }

    public double getProbabilita() {
        return probabilita;
    }
}
