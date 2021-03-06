package com.elmfer.parkour_recorder.gui;

import static com.elmfer.parkour_recorder.render.GraphicsHelper.getIntColor;

import java.io.IOException;
import java.util.Comparator;
import java.util.Random;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import com.elmfer.parkour_recorder.EventHandler;
import com.elmfer.parkour_recorder.animation.Easing;
import com.elmfer.parkour_recorder.animation.Property;
import com.elmfer.parkour_recorder.animation.Timeline;
import com.elmfer.parkour_recorder.config.ConfigManager;
import com.elmfer.parkour_recorder.gui.TimelineViewport.TimeStampFormat;
import com.elmfer.parkour_recorder.gui.alertbox.GuiAlertBox;
import com.elmfer.parkour_recorder.gui.alertbox.GuiConfirmationBox;
import com.elmfer.parkour_recorder.gui.alertbox.GuiNamerBox;
import com.elmfer.parkour_recorder.gui.alertbox.GuiTimeFormatBox;
import com.elmfer.parkour_recorder.gui.widgets.GuiButton;
import com.elmfer.parkour_recorder.gui.widgets.GuiIconifiedButton;
import com.elmfer.parkour_recorder.gui.widgets.GuiSlider;
import com.elmfer.parkour_recorder.parkour.Checkpoint;
import com.elmfer.parkour_recorder.parkour.ParkourFrame;
import com.elmfer.parkour_recorder.parkour.PlaybackSession;
import com.elmfer.parkour_recorder.parkour.ReplayViewerEntity;
import com.elmfer.parkour_recorder.parkour.Recording;
import com.elmfer.parkour_recorder.render.GraphicsHelper;
import com.elmfer.parkour_recorder.render.ModelManager;
import com.elmfer.parkour_recorder.render.ShaderManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

public class TimelineScreen extends GuiScreen
{
	public static TimeStampFormat timeStampFormat = ConfigManager.loadTimeFormat();
	
	private TimelineViewport timelineViewport = new TimelineViewport(this);
	protected SessionType session = SessionType.NONE;
	private State state = State.PAUSED;
	protected Timeline timeline;
	protected ReplayViewerEntity viewer = null;
	protected Checkpoint currentCheckpoint = null;
	
	//Settings widgets
	protected GuiSettingsButton settingsButton = new GuiSettingsButton(-1, 0, 0);
	protected GuiSlider speedSlider = new GuiSlider(-2, 0, 0, I18n.format("gui.timeline.speed") + ": ", "x", 0.01f, 4.0f, 1.0f);
	protected GuiButton formatSelect = new GuiButton(-3, 0, 0, I18n.format("gui.timeline.time_format"));

	private GuiAlertBox alertBox = null;
	
	public TimelineScreen()
	{	
		//Set session state
		if(EventHandler.session instanceof PlaybackSession)
		{
			PlaybackSession session = (PlaybackSession) EventHandler.session;	
			this.session = session.isPlaying() ? SessionType.PLAYBACK : SessionType.REPLAY;
		}
		
		if(session.isActive()) //Setup timeline object if there is a recording loaded
		{
			PlaybackSession session = (PlaybackSession) EventHandler.session;
			
			double duration = (session.recording.size() - 1) / 20.0;
			timeline = new Timeline(duration);
			Property framePos = new Property("framePos", 0.0, session.recording.size() - 1);
			timeline.addProperties(framePos);
			timeline.setTimePos(session.recording.startingFrame / 20.0);
		}
		else //Setup a dummy timline object
		{
			timeline = new Timeline(1.0);
			Property framePos = new Property("framePos", 0.0, 1.0);
			timeline.addProperties(framePos);
		}
	}
	
	@Override
	public void initGui()
	{
		//Create viewer entity and reset the player's arm
		if(session == SessionType.REPLAY)
		{
			viewer = new ReplayViewerEntity();
			mc.setRenderViewEntity(viewer);
			mc.player.prevRenderArmYaw = mc.player.renderArmYaw = mc.player.prevRotationYaw;
			mc.player.prevRenderArmPitch = mc.player.renderArmPitch = mc.player.prevRotationPitch;
		}
		
		//Taskbar buttons
		addButton(new GuiButton(0, 0, 0, I18n.format("gui.timeline.start_here")));
		addButton(new GuiButton(1, 0, 0, I18n.format("gui.timeline.load")));
		
		//Replay Control Buttons
		addButton(new GuiIconifiedButton(2, 0, 0, "rewind_button"));
		addButton(new GuiIconifiedButton(3, 0, 0, "play_button"));
		addButton(new GuiIconifiedButton(4, 0, 0, "pause_button"));
		addButton(new GuiIconifiedButton(5, 0, 0, "start_button"));
		addButton(new GuiIconifiedButton(6, 0, 0, "end_button"));
		addButton(new GuiIconifiedButton(7, 0, 0, "prev_frame_button"));
		addButton(new GuiIconifiedButton(8, 0, 0, "next_frame_button"));
		
		//Settings Menu's Widgets
		addButton(settingsButton);
		addButton(speedSlider);
		
		//Checkpoint Toolbar Buttons
		addButton(new GuiIconifiedButton(9, 0, 0, "add_checkpoint_button"));
		addButton(new GuiIconifiedButton(10, 0, 0, "remove_checkpoint_button"));
		addButton(new GuiIconifiedButton(11, 0, 0, "prev_checkpoint_button"));
		addButton(new GuiIconifiedButton(12, 0, 0, "next_checkpoint_button"));
		
		//Settings Menu's Widget
		addButton(formatSelect);
		
		//Init alertbox if active
		if(alertBox != null)
			alertBox.initGui();
	}
	
	@Override
	protected void actionPerformed(net.minecraft.client.gui.GuiButton button)
	{
		//Identification of the pressed button
		int buttonID = button.id;
		
		switch (buttonID) {
		case 0: //Start here button
			onGuiClosed();
			mc.displayGuiScreen(null);
			if(session == SessionType.REPLAY)
			{
				PlaybackSession session = (PlaybackSession) EventHandler.session;
				
				session.startAt((int) timeline.getProperty("framePos").getValue());
			}
			break;
		case 1: //Load button; Unused
			onGuiClosed();
			mc.displayGuiScreen(new LoadRecordingScreen());
			break;
		case 2: //Rewind
			timeline.rewind();
			break;
		case 3: //Play
			timeline.play();
			break;
		case 4: //Pause
			timeline.pause();
			break;
		case 5: //Stop and goto beginning
			timeline.stop();
			timeline.setFracTime(0.0);
			break;
		case 6: //Stop and goto end
			timeline.stop();
			timeline.setFracTime(1.0);
			break;
		case 7: //Step to the previous frame
		{
			int framePos = (int) timeline.getProperty("framePos").getValue();
			double oneTick = 1.0 / (timeline.getDuration() * 20.0);
			
			//Snap timeline pos to current frame
			timeline.setFracTime(framePos / (timeline.getDuration() * 20.0) + oneTick / 10.0);
			
			timeline.setFracTime(timeline.getFracTime() - oneTick);
			timeline.pause();
			break;
		}
		case 8: //Step to the next frame
		{
			int framePos = (int) timeline.getProperty("framePos").getValue();
			double oneTick = 1.0 / (timeline.getDuration() * 20.0);
			
			//Snap timeline pos to current frame
			timeline.setFracTime(framePos / (timeline.getDuration() * 20.0) + oneTick / 10.0);
			
			timeline.setFracTime(timeline.getFracTime() + oneTick);
			timeline.pause();
			break;
		}
		case 9: //Create Checkpoint
			GuiNamerBox namerBox = new GuiNamerBox(I18n.format("gui.timeline.add_checkpoint"), this, (String s) -> { return true; } , this::addCheckpoint);
			namerBox.initGui();
			namerBox.textField.setMaxStringLength(64);
			alertBox = namerBox;
			break;
		case 10: //Remove Current Checkpoint
			String message = I18n.format("gui.timeline.remove_checkpoint_?");
			message += currentCheckpoint.name.isEmpty() ? "" : " - " + currentCheckpoint.name;
			GuiConfirmationBox confirmBox = new GuiConfirmationBox(message, this::removeCheckpoint, this);
			confirmBox.initGui();
			alertBox = confirmBox;
			break;
		case 11: //Goto previous or current checkpoint
		{
			PlaybackSession session = (PlaybackSession) EventHandler.session;
			int framePos = (int) timeline.getProperty("framePos").getValue();
			double oneTick = 1.0 / (timeline.getDuration() * 20.0);
			
			//If the frame position is greater then the current checkpoint's position, set the frame position to the checkpoint's position.
			if(currentCheckpoint.frameNumber < framePos) timeline.setTimePos(currentCheckpoint.frameNumber / 20.0 + oneTick / 100.0);
			else //Frame position is on the current checkpoint, thus goto previous checkpoint
			{
				int currentCheckptIndex = session.recording.checkpoints.indexOf(currentCheckpoint);
				Checkpoint prevCheckpoint = session.recording.checkpoints.get(currentCheckptIndex - 1);
				
				timeline.setTimePos(prevCheckpoint.frameNumber / 20.0 + oneTick / 100.0);
			}
			timeline.pause();
			break;
		}
		case 12: //Goto next checkpoint
		{
			PlaybackSession session = (PlaybackSession) EventHandler.session;
			double oneTick = 1.0 / (timeline.getDuration() * 20.0);
			
			int currentCheckptIndex = session.recording.checkpoints.indexOf(currentCheckpoint);
			Checkpoint nextCheckpoint = session.recording.checkpoints.get(currentCheckptIndex + 1);
			
			timeline.setTimePos(nextCheckpoint.frameNumber / 20.0 + oneTick / 100.0);
			timeline.pause();
			break;
		}
		case -1: //Show Settings Menu
			GuiSettingsButton b = (GuiSettingsButton) button;
			if(b.gear.getState() == Timeline.State.REVERSE) {b.gear.play(); b.highlighed = true; }
			else {b.gear.rewind(); b.highlighed = false; }
			break;
		case -3: //Change time format
			GuiTimeFormatBox formattingBox = new GuiTimeFormatBox(this);
			formattingBox.initGui();
			alertBox = formattingBox;
		}
	}
	
	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks)
	{
		ScaledResolution res = new ScaledResolution(mc);
		timeline.tick();
		
		//Set frame position as the same from playback. Only occurs while playback is active
		if(session == SessionType.PLAYBACK)
			timeline.setTimePos((((PlaybackSession) EventHandler.session).getFrameNumber() + mc.getRenderPartialTicks()) / 20.0);
		else if(session == SessionType.REPLAY) //During Replay Mode
		{
			PlaybackSession session = (PlaybackSession) EventHandler.session;
			
			int currentFrame = (int) timeline.getProperty("framePos").getValue();
			float partialFrame = (float) (timeline.getProperty("framePos").getValue() - currentFrame);
			ParkourFrame prevFrame = session.recording.get(currentFrame);
			ParkourFrame frame = session.recording.get(Math.min(session.recording.size() - 1, currentFrame + 1));
			
			
			viewer.setState(frame, prevFrame, partialFrame); //Set the viewer's state.
		}
		
		//Styling Constants
		final int margin = GuiStyle.Gui.margin();
		final int smallMargin = GuiStyle.Gui.smallMargin();
		final int gradientHeight = 15;
		final int buttonHeight = GuiStyle.Gui.buttonHeight();
		final int backroundColor = getIntColor(GuiStyle.Gui.backroundColor());
		final int fade1 = getIntColor(GuiStyle.Gui.fade1());
		final int fade2 = getIntColor(GuiStyle.Gui.fade2());
		
		GuiSettingsButton settingsButton = (GuiSettingsButton) buttonList.get(9);
		
		//Structering of the GUI
		GuiViewport all = new GuiViewport(res);
		GuiViewport timelineBar = new GuiViewport(all);
		timelineBar.top = all.bottom - (smallMargin * 2 + buttonHeight * 2);
		GuiViewport taskBar = new GuiViewport(all);
		taskBar.bottom = smallMargin * 2 + buttonHeight;
		GuiViewport title = new GuiViewport(taskBar);
		title.top = title.left = smallMargin;
		title.bottom -= smallMargin;
		title.right = title.left + mc.fontRenderer.getStringWidth(I18n.format("gui.timeline")) + title.getHeight() - mc.fontRenderer.FONT_HEIGHT;
		GuiViewport timeline = new GuiViewport(timelineBar);
		timeline.top = smallMargin;
		timeline.left = smallMargin;
		timeline.bottom -= smallMargin;
		timeline.right -= smallMargin;
		GuiViewport controls = new GuiViewport(all); //Playback controls
		int controlsSize = (int) (timeline.getHeight() * 0.55f); 
		int controlsWidth = (controlsSize - 2 * smallMargin) * 7 + 7 * smallMargin + margin;
		controls.top = timelineBar.top - controlsSize - smallMargin;
		controls.bottom = controls.top + controlsSize;
		controls.left = all.getWidth() / 2 - controlsWidth / 2;
		controls.right = controls.left + controlsWidth;
		GuiViewport checkpointControls = new GuiViewport(all);
		int checkpointControlsWidth = (controlsSize - 2 * smallMargin) * 4 + 4 * smallMargin + margin;
		checkpointControls.top = controls.top;
		checkpointControls.bottom = controls.bottom;
		checkpointControls.left = smallMargin;
		checkpointControls.right = checkpointControls.left + checkpointControlsWidth;
		GuiViewport settingsBody = new GuiViewport(all);
		settingsBody.left = controls.right + smallMargin;
		settingsBody.top = (int) (timelineBar.top - (GuiStyle.Gui.buttonHeight() * 2 + smallMargin * 4) * settingsButton.gear.getProperty("height_mult").getValue());
		settingsBody.right -= smallMargin;
		settingsBody.bottom = timelineBar.top - smallMargin;
		GuiViewport settings = new GuiViewport(settingsBody);
		settings.left = settings.top = smallMargin;
		settings.right -= smallMargin;
		
		//Render the timeline bar
		timelineBar.pushMatrix(false);
		{
			drawGradientRect(0, -gradientHeight, taskBar.getWidth(), 0, fade2, fade1);
			drawRect(0, 0, timelineBar.getWidth(), timelineBar.getHeight(), getIntColor(0.0f, 0.0f, 0.0f, 0.7f));
			
			renderCheckpointControls(checkpointControls, mouseX, mouseY, partialTicks);
			renderControls(controls, mouseX, mouseY, partialTicks);
			timelineViewport.drawScreen(mouseX, mouseY, partialTicks, timeline);
			renderTaskbar(title, taskBar, mouseX, mouseY, partialTicks);
			renderCheckpointStatus(taskBar, mouseX, mouseY, partialTicks);
		}
		timelineBar.popMatrix();
		
		//Render settings if it's enabled
		if(settingsBody.getHeight() > smallMargin * 2)
		{
			settingsBody.pushMatrix(false);
			{
				drawRect(0, 0, settingsBody.getWidth(), settingsBody.getHeight(), backroundColor);
				settings.pushMatrix(true);
				{
					GuiSlider slider = (GuiSlider) buttonList.get(10);
					GuiButton formatChanger = (GuiButton) buttonList.get(15);
					slider.width = formatChanger.width = settings.getWidth();
					slider.height = formatChanger.height = 20;
					formatChanger.y = slider.height + smallMargin;
					
					this.timeline.setSpeed(slider.getSliderValue());
					formatChanger.displayString = I18n.format("gui.timeline.time_format") + ": " + timeStampFormat.NAME;
					
					slider.drawButton(mc, mouseX, mouseY, partialTicks);
					formatChanger.drawButton(mc, mouseX, mouseY, partialTicks);
				}
				settings.popMatrix();
			}
			settingsBody.popMatrix();
		}
		
		//Render the title
		title.pushMatrix(false);
		{
			drawRect(0, 0, title.getWidth(), title.getHeight(), getIntColor(0.0f, 0.0f, 0.0f, 0.5f));
			drawCenteredString(mc.fontRenderer, I18n.format("gui.timeline"), title.getWidth() / 2, title.getHeight() / 2 - mc.fontRenderer.FONT_HEIGHT / 2, 0xFFFFFFFF);
		}
		title.popMatrix();
		
		//If an alertbox is active, render it. Remove alertbox if it's set closed.
		if(alertBox != null)
		{
			alertBox.drawScreen(mouseX, mouseY, partialTicks);
			if(alertBox.shouldClose()) alertBox = null;
		}
	}
	
	@Override
	public void onGuiClosed()
	{
		super.onGuiClosed();
		
		mc.setRenderViewEntity(mc.player);
		if(viewer != null) { mc.world.removeEntityDangerously(viewer); }
		
		//Save time format to config file
		ConfigManager.saveTimeFormat(timeStampFormat);
	}
	
	@Override
	public void keyTyped(char keyTyped, int keyID)
	{
		if(alertBox != null) { alertBox.keyTyped(keyTyped, keyID); return; }
		try { super.keyTyped(keyTyped, keyID); } catch (IOException e) {}
	}
	
	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException 
	{
		if(alertBox != null)
		{
			alertBox.mouseClicked(mouseX, mouseY, mouseButton);
			return;
		}
		
		super.mouseClicked(mouseX, mouseY, mouseButton);
	}
	
	/**Gui won't pause game.**/
	@Override
	public boolean doesGuiPauseGame()
	{
	      return false;
	}
	
	/**Renders the checkpoint marker and the name of the current checkpoint, if any.**/
	private void renderCheckpointStatus(GuiViewport taskBar, int mouseX, int mouseY, float partialTicks)
	{
		if(currentCheckpoint != null)
		{
			//Styling Constants
			final int SMALL_MARGIN = GuiStyle.Gui.smallMargin();
			final int BACKROUND_COLOR = getIntColor(0.0f, 0.0f, 0.0f, 0.4f);
			final int FADE_2 = GraphicsHelper.getIntColor(GuiStyle.Gui.fade2());
			final int FADE_WIDTH = GuiStyle.Gui.fadeHeight();
			
			//String dimensions.
			final int STRING_LENGTH = currentCheckpoint.name != null ? fontRenderer.getStringWidth(currentCheckpoint.name) : 0;
			final float STRING_HEIGHT = mc.fontRenderer.FONT_HEIGHT * 2;

			//Render gradient backround
			int stringOffset = (int) (STRING_HEIGHT * 0.6f + SMALL_MARGIN);
			drawRect(SMALL_MARGIN, taskBar.bottom + SMALL_MARGIN, STRING_LENGTH + stringOffset, (int) (taskBar.bottom + SMALL_MARGIN + STRING_HEIGHT), BACKROUND_COLOR);
			GraphicsHelper.gradientRectToRight(STRING_LENGTH + stringOffset, taskBar.bottom + SMALL_MARGIN, SMALL_MARGIN + STRING_LENGTH + FADE_WIDTH + stringOffset, (int) (taskBar.bottom + SMALL_MARGIN + STRING_HEIGHT), BACKROUND_COLOR, FADE_2);
			
			//Checkpoint color
			Vector4f color = GraphicsHelper.getFloatColor(currentCheckpoint.color);
			
			//Render the checkpoint marker
			final int shader = ShaderManager.getGUIShader();
			final int prevShader = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
			GlStateManager.enableBlend();
			GlStateManager.color(color.getX(), color.getY(), color.getZ());
			GlStateManager.pushMatrix();
			{
				GlStateManager.translate(SMALL_MARGIN, taskBar.bottom + SMALL_MARGIN, 0.0f);
				GlStateManager.scale(STRING_HEIGHT, -STRING_HEIGHT, 1.0);
				
				GL20.glUseProgram(shader);
				GL20.glUniform4f(GL20.glGetUniformLocation(shader, "masterColor"), color.getX(), color.getY(), color.getZ(), 1.0f);
				ModelManager.renderModel("checkpoint_icon");
				GL20.glUseProgram(prevShader);
			}
			GlStateManager.popMatrix();
			
			//If checkpoint has a name, render it's name.
			if(!currentCheckpoint.name.isEmpty())
				drawString(fontRenderer, currentCheckpoint.name, stringOffset, (int) (taskBar.bottom + SMALL_MARGIN + STRING_HEIGHT / 2 - fontRenderer.FONT_HEIGHT / 2), 0xFFFFFFFF);
		}
	}
	
	/**Renders the top bar.**/
	private void renderTaskbar(GuiViewport title, GuiViewport taskBar, int mouseX, int mouseY, float partialTicks)
	{
		int buttonWidth = 100;
		boolean recordingIsLoaded = EventHandler.session instanceof PlaybackSession;
		
		//Styling Constants
		final int MARGIN = GuiStyle.Gui.margin();
		final int SMALL_MARGIN = GuiStyle.Gui.smallMargin();
		final int FADE_1 = getIntColor(GuiStyle.Gui.fade1());
		final int FADE_2 = getIntColor(GuiStyle.Gui.fade2());
		final int FADE_HEIGHT = GuiStyle.Gui.fadeHeight();
		final int BUTTON_HEIGHT = GuiStyle.Gui.buttonHeight();
		
		//Render Taskbar
		taskBar.pushMatrix(false);
		{
			drawGradientRect(0, taskBar.getHeight(), taskBar.getWidth(), taskBar.getHeight() + FADE_HEIGHT, FADE_1, FADE_2);
			drawRect(0, 0, taskBar.getWidth(), taskBar.getHeight(), getIntColor(0.0f, 0.0f, 0.0f, 0.7f));
			
			drawVerticalLine(title.right + MARGIN, SMALL_MARGIN, taskBar.getHeight() - SMALL_MARGIN * 2, getIntColor(0.4f, 0.4f, 0.4f, 0.5f));
			
			String recordingName = recordingIsLoaded ? ((PlaybackSession) EventHandler.session).recording.getName() : I18n.format("gui.timeline.no_recording_is_loaded");
			int nameColor = recordingIsLoaded ? 0xFFFFFFFF : getIntColor(0.5f, 0.5f, 0.5f, 1.0f);
			
			drawString(mc.fontRenderer, recordingName, title.right + MARGIN * 2, taskBar.getHeight() / 2 - mc.fontRenderer.FONT_HEIGHT / 2, nameColor);
			
			drawVerticalLine(title.right + MARGIN * 3 + mc.fontRenderer.getStringWidth(recordingName), SMALL_MARGIN, taskBar.getHeight() - SMALL_MARGIN * 2, getIntColor(0.4f, 0.4f, 0.4f, 0.5f));
			
			GuiButton start = (GuiButton) buttonList.get(0);
			GuiButton load = (GuiButton) buttonList.get(1);
			
			//Update the buttons' states
			start.height = BUTTON_HEIGHT;
			load.height = BUTTON_HEIGHT;
			start.setWidth(buttonWidth);
			load.setWidth(buttonWidth);
			start.x = title.right + MARGIN * 4 + mc.fontRenderer.getStringWidth(recordingName);
			load.x = start.x + buttonWidth + MARGIN;
			start.y = load.y = SMALL_MARGIN;
			start.enabled = session == SessionType.REPLAY;
			load.visible = false;
			
			start.drawButton(mc, mouseX, mouseY, partialTicks);
			load.drawButton(mc, mouseX, mouseY, partialTicks);
		}
		taskBar.popMatrix();
	}
	
	/**Renders the Checkpoint toolbar**/
	private void renderCheckpointControls(GuiViewport checkpointControls, int mouseX, int mouseY, float partialTicks)
	{
		int framePos = (int) timeline.getProperty("framePos").getValue();
		
		//Styling Constants
		final int SMALL_MARGIN = GuiStyle.Gui.smallMargin();
		final int MARGIN = GuiStyle.Gui.margin();
		
		//Render toolbar
		checkpointControls.pushMatrix(false);
		{
			GuiButton addCheckpoint = (GuiButton) buttonList.get(11);
			GuiButton removeCheckpoint = (GuiButton) buttonList.get(12);
			GuiButton prevCheckpoint = (GuiButton) buttonList.get(13);
			GuiButton nextCheckpoint = (GuiButton) buttonList.get(14);
			GuiButton[] buttonControls = {addCheckpoint, removeCheckpoint, prevCheckpoint, nextCheckpoint};
			
			currentCheckpoint = null;
			addCheckpoint.enabled = this.session.isActive();
			removeCheckpoint.enabled = prevCheckpoint.enabled = nextCheckpoint.enabled = false;
			
			//Get current checkpoint and update the buttons' states
			if(EventHandler.session instanceof PlaybackSession)
			{
				PlaybackSession session = (PlaybackSession) EventHandler.session;
				Recording recording = session.recording;
				
				for(Checkpoint c : session.recording.checkpoints)
				{
					if(framePos < c.frameNumber) break;
					currentCheckpoint = c;
				}
				
				if(currentCheckpoint != null) 
					removeCheckpoint.enabled = true;
				
				if(!recording.checkpoints.isEmpty() && framePos < recording.checkpoints.get(recording.checkpoints.size() - 1).frameNumber)
					nextCheckpoint.enabled = true;
				if(!recording.checkpoints.isEmpty() && recording.checkpoints.get(0).frameNumber < framePos)
					prevCheckpoint.enabled = true;
			}
			
			//Buttons' width and height
			int size = checkpointControls.getHeight() - SMALL_MARGIN * 2;
			
			//Set buttons's size and position
			int i = 0;
			for(GuiButton button : buttonControls)
			{
				button.setWidth(size);
				button.height = size;
				button.y = SMALL_MARGIN;
				button.x = SMALL_MARGIN + (size + SMALL_MARGIN) * i;
				i++;
			}
			prevCheckpoint.x = removeCheckpoint.x + removeCheckpoint.width + MARGIN;
			nextCheckpoint.x = prevCheckpoint.x + prevCheckpoint.width + SMALL_MARGIN;
			
			//Draw backround and buttons
			drawRect(0, 0, checkpointControls.getWidth(), checkpointControls.getHeight(), getIntColor(GuiStyle.Gui.backroundColor()));
			addCheckpoint.drawButton(mc, mouseX, mouseY, partialTicks);
			removeCheckpoint.drawButton(mc, mouseX, mouseY, partialTicks);
			prevCheckpoint.drawButton(mc, mouseX, mouseY, partialTicks);
			nextCheckpoint.drawButton(mc, mouseX, mouseY, partialTicks);
		}
		checkpointControls.popMatrix();
	}
	
	/**Renders the replay toolbar**/
	private void renderControls(GuiViewport controls, int mouseX, int mouseY, float partialTicks)
	{	
		//Style constants
		final int MARGIN = GuiStyle.Gui.margin();
		final int SMALL_MARGIN = GuiStyle.Gui.smallMargin();
		
		//Render toolbar
		controls.pushMatrix(false);
		{
			GuiButton rewind = (GuiButton) buttonList.get(2);
			GuiButton play = (GuiButton) buttonList.get(3);
			GuiButton pause = (GuiButton) buttonList.get(4);
			GuiButton atBegginning = (GuiButton) buttonList.get(5);
			GuiButton atEnd = (GuiButton) buttonList.get(6);
			GuiButton settings = (GuiButton) buttonList.get(9);
			GuiButton prevFrame = (GuiButton) buttonList.get(7);
			GuiButton nextFrame = (GuiButton) buttonList.get(8);
			GuiButton[] buttonControls = {atBegginning, prevFrame, rewind, play, nextFrame, atEnd, settings, pause};
			
			//Buttons' width and height.
			int size = controls.getHeight() - SMALL_MARGIN * 2;
			
			//Set buttons's size and position
			int i = 0;
			for(GuiButton control : buttonControls)
			{
				control.setWidth(size);
				control.height = size;
				control.y = SMALL_MARGIN;
				control.x = i * (size + SMALL_MARGIN) + SMALL_MARGIN;
				i++;
			}
			
			//Update current replay state
			if(!(timeline.isPaused() || timeline.hasStopped()))
			{
				switch(timeline.getState())
				{
				case FORWARD:
					state = State.PLAYING;
					break;
				case REVERSE:
					state = State.REWINDING;
					break;
				}
			}
			else state = State.PAUSED;
			
			//Set the pause and settings button specific sizes and positions
			pause.setWidth(size * 2 + SMALL_MARGIN);
			if(state.isActive()) { pause.visible = true; play.visible = rewind.visible = false;}
			else { pause.visible = false; play.visible = rewind.visible = true; }
			settings.x = atEnd.x + atEnd.width + MARGIN;
			pause.x = rewind.x;
			
			//Disable all buttons (except settings) if GUI is not in Replay Mode
			if(session == SessionType.PLAYBACK || session == SessionType.NONE)
				for(GuiButton control : buttonControls) control.enabled = false;
			settings.enabled = true;
			
			//Render backround and buttons
			drawRect(0, 0, controls.getWidth(), controls.getHeight(), getIntColor(GuiStyle.Gui.backroundColor()));
			GlStateManager.disableCull();
			for(GuiButton control : buttonControls)
				control.drawButton(mc, mouseX, mouseY, partialTicks);	
			settings.drawButton(mc, mouseX, mouseY, partialTicks);
		}
		controls.popMatrix();
	}

	private void removeCheckpoint()
	{
		//Remove checkpoint
		PlaybackSession session = (PlaybackSession) EventHandler.session;
		session.recording.checkpoints.remove(currentCheckpoint);
		
		//Automatically save the recording
		session.recording.save(true, false, true);
	}
	
	private void addCheckpoint(String checkpointName)
	{
		final Comparator<Checkpoint> SORTER = (Checkpoint c1, Checkpoint c2) -> 
		{
			if(c1.frameNumber < c2.frameNumber) return -1;
			else if(c2.frameNumber < c1.frameNumber) return 1;
			return 0;
		};
		
		PlaybackSession session = (PlaybackSession) EventHandler.session;
		int framePos = (int) timeline.getProperty("framePos").getValue();
		
		//Remove any checkpoints with the same frame number.
		session.recording.checkpoints.removeIf((Checkpoint c) -> { return c.frameNumber == framePos; });
		
		Checkpoint checkpoint = new Checkpoint(checkpointName, framePos);
		
		//Generate random color and assign it to new checkpoint
		Random rand = new Random();
		float red = rand.nextFloat() * 0.5f + 0.3f;
		float green = rand.nextFloat() * 0.5f + 0.3f;
		float blue = rand.nextFloat() * 0.5f + 0.3f;
		checkpoint.color = GraphicsHelper.getIntColor(red, green, blue, 1.0f);
		
		//Add checkpoint and sort the list
		session.recording.checkpoints.add(checkpoint);
		session.recording.checkpoints.sort(SORTER);
		
		//Automatically save the recording
		session.recording.save(true, false, true);
	}
	
	/**Specialized Iconified Button With Animations**/
	protected static class GuiSettingsButton extends GuiIconifiedButton
	{
		Timeline gear = new Timeline(0.2);
		
		public GuiSettingsButton(int id, int x, int y) {
			super(id, x, y, "settings_button");
			
			//Setup animation properties
			gear.addProperties(new Property("rotation", 0.0, -180.0, Easing.INOUT_QUAD));
			gear.addProperties(new Property("height_mult", 0.0, 1.0, Easing.INOUT_QUAD));
			gear.rewind();
			gear.stop();
		}
		
		@Override
		public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks)
		{
			if(visible)
			{
				//Styling Constats
				float SMALL_MARGIN = GuiStyle.Gui.smallMargin() * 2;
				float MODEL_SCALE = height - SMALL_MARGIN * 2;
				
				//Set Animation State
				if(hovered) { animation.queue("hovered"); animation.play(); animation.apply();}
				else { animation.queue("hovered"); animation.rewind(); animation.apply();}	
				if(highlighed) { animation.queue("highlight"); animation.play(); animation.apply();}
				else { animation.queue("highlight"); animation.rewind(); animation.apply();}
				if(!enabled) {animation.queue("hovered", "highlight"); animation.rewind(); animation.apply();}
				
				//Update animation
				animation.tick();
				gear.tick();
				
				preRender(mouseX, mouseY, partialTicks);
				
				//Calculate Button's color
				Vector3f hoveredcolor = new Vector3f(0.3f, 0.3f, 0.3f);
				hoveredcolor.scale((float) animation.getTimeline("hovered").getFracTime());
				Vector3f highlightColor = new Vector3f(highlightTint);
				highlightColor.scale((float) (animation.getTimeline("highlight").getFracTime() * 0.6));
				Vector3f c = hoveredcolor;
				c = Vector3f.add(c, highlightColor, null);
				int color = getIntColor(c, 0.2f);
				
				//Caclulate Icon Color
				int j = 14737632;
				if (!enabled)
		            j = GraphicsHelper.getIntColor(0.45f, 0.45f, 0.45f, 1.0f);
		        else if (hovered)
		            j = 16777120;
				
				//Draw backround
				drawRect(x, y, x + width, y + height, color);
				
				//Render Gear Icon
				int shader = ShaderManager.getGUIShader();
				int prevShader = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
				Vector4f color1 = GraphicsHelper.getFloatColor(j);
				color1.setW(1.0f);
				GlStateManager.disableTexture2D();
				GlStateManager.enableBlend();
				GlStateManager.color(color1.getX(), color1.getY(), color1.getZ());
				GlStateManager.pushMatrix();
				{
					GlStateManager.translate(x + width / 2, y + height / 2, 0.0f);
					GlStateManager.scale(MODEL_SCALE, -MODEL_SCALE, 1.0f);
					GlStateManager.rotate((float) gear.getProperty("rotation").getValue(), 0, 0, 1);
					
					GL20.glUseProgram(shader);
					GL20.glUniform4f(GL20.glGetUniformLocation(shader, "masterColor"), color1.getX(), color1.getY(), color1.getZ(), color1.getW());
					ModelManager.renderModel(modelName);
					GL20.glUseProgram(prevShader);
				}
				GlStateManager.popMatrix();
			}
		}
	}
	
	protected static enum SessionType
	{
		REPLAY, PLAYBACK, NONE;
		
		public boolean isActive()
		{
			return this == REPLAY || this == PLAYBACK;
		}
	}
	
	private static enum State
	{
		PLAYING, REWINDING, PAUSED;
		
		public boolean isActive()
		{
			return this == PLAYING || this == REWINDING;
		}
	}
}
