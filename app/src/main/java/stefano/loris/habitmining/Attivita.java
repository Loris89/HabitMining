package stefano.loris.habitmining;

import android.os.Parcel;
import android.os.Parcelable;

public class Attivita implements Parcelable {

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
    protected Attivita(Parcel in) {
        nome = in.readString();
        probabilita = in.readDouble();
        timestampStart = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(nome);
        dest.writeDouble(probabilita);
        dest.writeString(timestampStart);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Attivita> CREATOR = new Parcelable.Creator<Attivita>() {
        @Override
        public Attivita createFromParcel(Parcel in) {
            return new Attivita(in);
        }

        @Override
        public Attivita[] newArray(int size) {
            return new Attivita[size];
        }
    };
}
