
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Random;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.fixedfunc.GLMatrixFunc;
import javax.media.opengl.glu.GLU;

import IFS.DrawableIFS;
import geometry.IntersectionOps;
import geometry.RegularPolygon;
import reusable.Helpers;
import reusable.graphicsPrimitives.Mat2;
import reusable.graphicsPrimitives.Vec2;
import snowflakes.SnowFlurry;


/**
 * A class that gets hooked into the GLCanvas that tracks updates and draws to the screen on refreshes.
 * Keeps track of state information on winter scenes and summer scenes
 * @author DEMcKnight
 */
public class EventManager implements GLEventListener, KeyListener, MouseListener, MouseMotionListener
{

	//********GENERAL APPLICATION STATE INFORMATION********//

	// The width and height of the our application virtually (i.e., in the world of the models)
	/**
	 * The virtual vertical resolution of our application (i.e., in the world of the models)
	 */
	private static int virtualWidth=1920;
	/**
	 * The virtual horizontal resolution of our application (i.e., in the world of the models)
	 */
	private static int virtualHeight=1080;

	// The width and height of our window in pixels
	/**
	 * The width of the window in pixels
	 */
	private static int screenWidth = 1920;
	/**
	 * The height of the window in pixels.
	 */
	private static int screenHeight = 1080;

	//Information about the scaling factor used in keeping contents in window and for letterboxing.
	/**
	 * Keeps track of the scale factor in the y-direction of the scene. Used when finding the world coordinates of the mouse cursor.
	 */
	private static double actualScaleY = 0;

	// Various vertical boundary constants for the program
	/**
	 * The horizon of the ground. Where the ground, sky, and tree end and begin.
	 */
	private final static int HORIZON = 275;
	/**
	 * Where the mountains begin
	 */
	private final static int MOUNTAIN_HORIZON = HORIZON - 2;
	/**
	 * The point below which clouds will not be drawn
	 */
	private final static int LOWEST_CLOUD_LEVEL = HORIZON + 325;

	//OpenGL transformation matrices
	/**
	 * The OpenGL Viewport rectangle vector (x, y, width, height)
	 */
	int[] viewport = new int[4];
	/**
	 * The OpenGL 4x4 projection transformation matrix
	 */
	private double[] projectionMatrix = new double[16];
	/**
	 * The OpenGL 4x4 modelview transformation matrix
	 */
	private double[] modelMatrix = new double[16];

	//Camera and mouse positions
	/**
	 * The camera position origin
	 */
	private Point2D.Double cameraOrigin = new Point2D.Double(0, 0);
	/**
	 * The mouse position, in virtual (world) coordinates
	 */
	private Point2D.Double mousePosition = new Point2D.Double(0, 0);


	//********INFORMATION USED BY BOTH SEASONS********//

	/**
	 * The polygon that's used for drawing the Sun (in summer) or the Moon (in winter)
	 */
	public static RegularPolygon theSunOrMoon = new RegularPolygon(new Vec2(1920*.9, 1080*.9), 0, 70, 30);

	/**
	 * Whether the season changed recently. If true, then call init on the new season
	 */
	public static boolean seasonChanged = false;
	/**
	 * Whether to draw the winter scene. If false, the summer scene is drawn.
	 */
	private static boolean drawWinter = true;


	//*********WINTER INFORMATION***********//
	/**
	 * An iterated functions system for drawing the winter tree.
	 */
	public static DrawableIFS treeIFS;

	/**
	 * An iterated function system for drawing the winter ground.
	 */
	public static DrawableIFS groundIFS;

	/**
	 * A SnowFlurry object for dropping lots of Snowflake objects into and in the winter scene
	 */
	public static SnowFlurry flurry;


	/******************************************/
	/*GLEventListener methods*/
	/******************************************/

	//Called by the drawable to initiate OpenGL rendering by the client.
	@Override
	public void display(GLAutoDrawable drawable)
	{
		GL2 gl = drawable.getGL().getGL2();

		//Set the clear color to black
		gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f );
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

		//Update the scene every frame
		update();
		updateProjectionMatrix(drawable);

		//Decide whether to draw summer stuff or winter stuff
		if (!drawWinter)
			renderSummer(drawable);
		else
			renderWinter(drawable);
	}

  //Called by the drawable when the display mode or the display device associated with the GLAutoDrawable has changed.
	@Override
	public void dispose(GLAutoDrawable arg0){}

	//Called by the drawable immediately after the OpenGL context is initialized.
	@Override
	public void init(GLAutoDrawable canvas)
	{
		initSummer();
		initWinter();
	}

	//Called by the drawable during the first repaint after the component has been resized.
	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height)
	{
		screenWidth = width;
		screenHeight = height;
	}


	/******************************************/
	/*Render methods*/
	/******************************************/

	/**
	 * Renders the summer scene to the given GLAutoDrawable
	 * @param drawable
	 */
	public static void renderSummer(GLAutoDrawable drawable)
	{
		GL2 gl = drawable.getGL().getGL2();

		//Draw sky background
		Drawers.drawSkyRect(gl, new Color[]{new Color(2, 125, 254), new Color(82, 192, 255), new Color(188, 245, 255)}, 0, 1920, HORIZON, 1080);

		//Draw the ground
		Drawers.drawGroundRect(gl,  new Color(82, 63, 63), new Color(97, 143, 81), 0, 1920, 0, HORIZON-1);

		//Draw the sun
		Helpers.setColor(gl, Color.YELLOW);
		Helpers.drawPolygon(gl, theSunOrMoon.boundaryPoints);
	}


	/**
	 * Renders the winter scene to the given GLAutoDrawable
	 * @param drawable
	 */
	public static void renderWinter(GLAutoDrawable drawable)
	{
		GL2 gl = drawable.getGL().getGL2();

		//Cover the screen with blackness
		Drawers.drawGroundRect(gl, Color.BLACK, Color.BLACK, 0, 1920, 0, 1080);

		//Draw the moon
		Helpers.setColor(gl, Color.WHITE);
		Helpers.drawPolygon(gl, theSunOrMoon.boundaryPoints);

		//We'll draw most of the flakes white
		Helpers.setColor(gl, Color.WHITE);

		gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
		gl.glPushMatrix();
		gl.glTranslated(670, 0, 0);
		treeIFS.draw(gl);
		gl.glPopMatrix();

		groundIFS.draw(gl);

		flurry.draw(gl);
	}


	/******************************************/
	/*Initialization methods*/
	/******************************************/

	/**
	 * Initializes/prepares variables being used for the summer scene
	 */
	public static void initSummer()
	{
	}

	/**
	 * Initializes/prepares variables being used for the winter scene
	 */
	public static void initWinter()
	{
		//Prepare IFS values for the tree (modified from parameters' source Paul Bourke at http://paulbourke.net/fractals/)
		ArrayList<Mat2> treeLinearTransforms = new ArrayList<Mat2>();
		treeLinearTransforms.add(new Mat2(0.195,-0.488,0.344,0.443));
		treeLinearTransforms.add(new Mat2(0.462,0.414,-0.252,0.361));
		treeLinearTransforms.add(new Mat2(-0.6395,0,0,0.501));
		treeLinearTransforms.add(new Mat2(-0.035,0.07,-0.469,0.022));
		treeLinearTransforms.add(new Mat2(-0.058,-0.07,0.453,-0.111));

		ArrayList<Vec2> treeTranslations = new ArrayList<Vec2>();
		treeTranslations.add(new Vec2(0.4431,0.2452));
		treeTranslations.add(new Vec2(0.2511,0.5692));
		treeTranslations.add(new Vec2(0.8562,0.2512));
		treeTranslations.add(new Vec2(0.4884,0.5069));
		treeTranslations.add(new Vec2(0.5976,0.0969));

		//Prepare the tree IFS
		treeIFS = new DrawableIFS(new Vec2(0, HORIZON*0.9), 500, 500, treeLinearTransforms, treeTranslations);


		//Prepare IFS values for the ground
		ArrayList<Mat2> groundLinearTransforms = new ArrayList<Mat2>();
		groundLinearTransforms.add(new Mat2(0.5,0,0,0.5));
		groundLinearTransforms.add(new Mat2(0.5,0,0,0.5));
		groundLinearTransforms.add(new Mat2(0.5,0,0,0.5));
		groundLinearTransforms.add(new Mat2(0.5,0,0,0.5));

		ArrayList<Vec2> groundTranslations = new ArrayList<Vec2>();
		groundTranslations.add(new Vec2(0,0));
		groundTranslations.add(new Vec2(0.5,0));
		groundTranslations.add(new Vec2(0,0.5));
		groundTranslations.add(new Vec2(0.5,0.5));

		//Prepare the ground IFS
		groundIFS = new DrawableIFS(new Vec2(0, 0), virtualWidth, HORIZON, groundLinearTransforms, groundTranslations);

		//Prepare the flurry of Snowflakes
		flurry = new SnowFlurry(0, virtualWidth, HORIZON, 0, virtualHeight, 1/3.0);
	}


	/******************************************/
	/*Update methods*/
	/******************************************/

	/**
	 * Method for updating contents of this scene
	 */
	private void update()
	{
		//If the season recently changed, we need to reinitialize things
		if (seasonChanged)
		{
			if (!drawWinter)
				initSummer();
			else
				initWinter();
			seasonChanged =false;
		}

		//If it's summer, update summer stuff
		if (!drawWinter)
		{
		}

		//If it's winter, update winter stuff
		if(drawWinter)
		{
			//Advance the IFSs by 10 iterations
			for (int i=0; i<10; i++)
			{
				treeIFS.iterate();
				groundIFS.iterate();
			}
			//Advance the snowflakes by a sixtieth of a second
			flurry.iterate(1/60.0);
		}
	}

	/**
	 * Updates the projection matrix, keeping contents on screen and providing a letterboxing/pillarboxing effect
	 * @param drawable
	 */
	private void updateProjectionMatrix(GLAutoDrawable drawable)
	{
		GL2 gl = drawable.getGL().getGL2();

		//Project to the window
		gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
		gl.glLoadIdentity();

		//Scale what's being drawn to account for changes to the window
		float scaleX = virtualWidth/(float)screenWidth;
		float scaleY = virtualHeight/(float)screenHeight;
		if (scaleX > scaleY)
			scaleY = scaleX;
		else
			scaleX = scaleY;

		//Store the actual amount Y is being scaled for finding the world coordinates of the mouse later
		actualScaleY = scaleY;

		//Fit the image to the screen and letterbox/pillarbox it if needed
		gl.glOrtho(cameraOrigin.x, (screenWidth + cameraOrigin.x)*scaleX, cameraOrigin.y, (screenHeight + cameraOrigin.y)*scaleY, 0, 1);

		//Update projection matrices
		gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);
        gl.glGetDoublev(GLMatrixFunc.GL_PROJECTION_MATRIX, projectionMatrix, 0);
        gl.glGetDoublev(GLMatrixFunc.GL_MODELVIEW_MATRIX,  modelMatrix, 0);

	}


	/******************************************/
	/*Private methods*/
	/******************************************/




	/******************************************/
	/*KeyListener Methods*/
	/******************************************/

	@Override
	public void keyPressed(KeyEvent e){}

	@Override
	public void keyReleased(KeyEvent e){}

	@Override
	public void keyTyped(KeyEvent e){}



	/******************************************/
	/*MouseListener Methods*/
	/******************************************/

	@Override
	public void mouseMoved(MouseEvent e)
	{
		updateMousePosition(e);
	}
	
	@Override
	public void mouseDragged(MouseEvent e)
	{
		updateMousePosition(e);
	}

	@Override
	public void mouseClicked(MouseEvent e)
	{
		updateMousePosition(e);

		if (IntersectionOps.isPointInsidePoly(new Vec2(mousePosition.getX(), mousePosition.getY()), theSunOrMoon.boundaryPoints))
		{
			drawWinter = !drawWinter;
			seasonChanged = true;
		}
	}

	@Override
	public void mouseEntered(MouseEvent e)
	{
		updateMousePosition(e);
	}

	@Override
	public void mouseExited(MouseEvent e)
	{
		updateMousePosition(e);
	}

	@Override
	public void mousePressed(MouseEvent e)
	{
		updateMousePosition(e);
	}

	@Override
	public void mouseReleased(MouseEvent e)
	{
		updateMousePosition(e);
	}

	/**
	 * Updates the mousePosition variable by transforming the MouseEvent's position to world coordinates
	 * @param e The MouseEvent that triggered the method that called for this mouse position update.
	 */
	public void updateMousePosition(MouseEvent e)
	{
		double wCoord[] = new double[4];// wx, wy, wz;// returned xyz coords

		//Transform the window mouse coordinates into world mouse coordinates
		new GLU().gluUnProject(e.getX(), e.getY(), 0.0, //
	              modelMatrix, 0,
	              projectionMatrix, 0,
	              viewport, 0,
	              wCoord, 0);

		//Update the mouse position
		mousePosition = new Point2D.Double(wCoord[0], (((screenHeight + cameraOrigin.y)*actualScaleY)-wCoord[1]));
	}
}
