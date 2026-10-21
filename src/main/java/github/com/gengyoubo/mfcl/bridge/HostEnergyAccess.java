package github.com.gengyoubo.mfcl.bridge;

/** Host-owned FE endpoint, in RF units (1:1); sides 0..5 match ForgeDirection, 6 means UNKNOWN/null. */
public interface HostEnergyAccess {
    boolean canReceive(int side);
    boolean canExtract(int side);
    int stored(int side);
    int capacity(int side);
    int receive(int side,int amount,boolean simulate);
    int extract(int side,int amount,boolean simulate);
}
