//made with love by nyxon

package com.modern.core.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Base64;

public final class ItemStacks {

  private ItemStacks() {
  }

  public static String encodePlayer(PlayerInventory inventory) {
    ItemStack[] packed = new ItemStack[41];
    for (int i = 0; i < 36; i++) {
      packed[i] = inventory.getItem(i);
    }
    packed[36] = inventory.getHelmet();
    packed[37] = inventory.getChestplate();
    packed[38] = inventory.getLeggings();
    packed[39] = inventory.getBoots();
    packed[40] = inventory.getItemInOffHand();
    return encode(packed);
  }

  public static String encode(ItemStack[] items) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      DataOutputStream data = new DataOutputStream(out);
      data.writeInt(items == null ? 0 : items.length);
      if (items != null) {
        for (ItemStack item : items) {
          if (item == null || item.getType().isAir()) {
            data.writeInt(0);
            continue;
          }
          byte[] bytes = item.serializeAsBytes();
          data.writeInt(bytes.length);
          data.write(bytes);
        }
      }
      data.flush();
      return Base64.getEncoder().encodeToString(out.toByteArray());
    } catch (Exception exception) {
      return "";
    }
  }

  public static void restorePlayer(PlayerInventory inventory, ItemStack[] packed) {
    for (int i = 0; i < 36; i++) {
      inventory.setItem(i, copyAt(packed, i));
    }
    inventory.setHelmet(copyAt(packed, 36));
    inventory.setChestplate(copyAt(packed, 37));
    inventory.setLeggings(copyAt(packed, 38));
    inventory.setBoots(copyAt(packed, 39));
    inventory.setItemInOffHand(copyAt(packed, 40));
  }

  private static ItemStack copyAt(ItemStack[] packed, int index) {
    if (packed == null || index >= packed.length || packed[index] == null || packed[index].getType().isAir()) {
      return null;
    }
    return packed[index].clone();
  }

  public static ItemStack[] decode(String raw) {
    if (raw == null || raw.isBlank()) {
      return new ItemStack[0];
    }
    try {
      DataInputStream data = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(raw)));
      int length = data.readInt();
      ItemStack[] items = new ItemStack[Math.max(0, length)];
      for (int i = 0; i < items.length; i++) {
        int size = data.readInt();
        if (size <= 0) {
          continue;
        }
        byte[] bytes = data.readNBytes(size);
        items[i] = ItemStack.deserializeBytes(bytes);
      }
      return items;
    } catch (Exception exception) {
      return new ItemStack[0];
    }
  }
}
