package github.com.gengyoubo.mfcl.bridge;

/** Host-owned item storage seen through its unsided handler; stacks are old representations, null for empty or unmapped slots. */
public interface HostInventoryAccess {
    boolean live();
    int size();
    LegacyClientData.Stack get(int slot);
    int limit(int slot);
    /** How many of {@code stack} the slot takes; a simulation never changes the host. */
    int insert(int slot, LegacyClientData.Stack stack, boolean simulate);
    /** How many items left the slot. */
    int extract(int slot, int amount, boolean simulate);
}
