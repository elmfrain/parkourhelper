package com.elmfer.parkour_recorder.gui;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.resources.I18n;

public class GuiConfirmationBox extends GuiAlertBox {
	
	private ICallback callback;
	
	public GuiConfirmationBox(String title, ICallback func, GuiScreen parent)
	{
		super(title, parent);
		callback = func;
	}
	
	public static interface ICallback
	{
		public void callBack();
	}
	
	@Override
	public void init()
	{
		super.init();
		addButton(new GuiButton(0, 0, I18n.format("gui.confirmation_box.yes"), this::confirmed));
		addButton(new GuiButton(0, 0, I18n.format("gui.confirmation_box.cancel"), this::close));
		height = 20;
	}

	@Override
	protected void doDrawScreen(MatrixStack stack, int mouseX, int mouseY, float partialTicks)
	{
		MainWindow res = Minecraft.getInstance().getMainWindow();
		viewport.pushMatrix(false);
		{
			int margins = (int) (20 / res.getGuiScaleFactor());
			
			GuiButton yes = (GuiButton) buttons.get(1);
			GuiButton cancel = (GuiButton) buttons.get(2);
			yes.setWidth(viewport.getWidth() / 2 - margins);
			cancel.setWidth(yes.width());
			yes.setY(viewport.getHeight() / 2 - yes.getHeight() / 2);
			cancel.setY(yes.y());;
			cancel.setX(viewport.getWidth() - cancel.width());
			yes.renderButton(stack, mouseX, mouseY, partialTicks);
			cancel.renderButton(stack, mouseX, mouseY, partialTicks);
		}
		viewport.popMatrix();
	}
	
	@Override
	public boolean keyPressed(int keyID, int scancode, int mods)
	{
		if(keyID == GLFW.GLFW_KEY_ENTER)
			confirmed(null);
		return false;
	}
	
	private void confirmed(@Nullable Button button)
	{
		callback.callBack();
		setShouldClose(true);
	}
}
