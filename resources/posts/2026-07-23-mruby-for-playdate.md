---
title: "MRuby on a Playdate"
accent-color: 57
---

I've been making Playdate games with the kiddos for a couple of years now. Most of these games have been done during Lisp Game Jams, giving me a chance to use a language I don't interact with much day to day. For Playdate, which is primarily a Lua platform, this means using Fennel, a Lisp that transpiles to Lua.

This has gotten me through making a few different games now, but as the kids get older and want to program their own games, Fennel is definitely a tougher "beginner" language so I've been thinking about something I'm more familiar with: Ruby. Playdate has a C SDK, and Ruby has an embeddable language fork called "MRuby" that can be integrated tightly with C, so the two seem like they could be a good fit!

Building this out required a few steps, first getting a working version of MRuby, then finding out how to get it into an actual Playdate project, then finally learning how to wrap the SDK correctly, but with all the pieces in place, now I've gotten things to a point that we may actually start building games! 

## Getting MRuby

MRuby offers itself in two forms: 1) a standard library with individual files you can reference from C and 2) an amalgam build that combines all of MRuby into a single C file. I settled early on for going with the amalgam build so I didn't have to think too hard about how to reference new files during compilation with my rusty C skills - figuring that if I get the amalgam working once and all the ruby functions I need are set up.

Amalgam builds take into account the options you might want for the build you're making - with Playdate we need to ensure we're set for 32 bit as an option - as well as removing any extensions reliant on standard io (which Playdate doesn't have C support for). Once I got a working amalgam build it was pretty straightforward to include it at the top of my main.c file. Getting a working amalgam was much harder than it should have been because I'm building this on an old personal laptop which is Windows based - I had a lot of trouble running the rake tasks there so had to build on a separate machine and email it to myself.


## Hello Ruby 

With the amalgam built and available I could finally try compiling a Playdate project with Ruby injected. The plan was to use mrbc to compile a `.rb` file into a `.c` file - mrbc can compile ruby to bytecode as either a binary `.mrbc` or a simple `.c` file with a static string of bytecode you can execute. (I called this constant `ruby_source` in my setup.) The easiest version of this is something like `(1 + 2).to_s` that we can execute and test basic math + stringify the result.

```ruby foo.rb
(1 + 2).to_s
```

```sh
mrbc -Bruby_source foo.rb
```

This gave me C that looks like this - bytecode that should be runnable on mruby:

```c foo.c
const uint8_t ruby_souce[] = {
0x52,0x49,0x54,0x45,0x30,0x34,0x30,0x30,0x00,0x00,0x00,0x4b,0x48,0x53,0x4d,0x4b,
0x30,0x30,0x30,0x30,0x49,0x52,0x45,0x50,0x00,0x00,0x00,0x2f,0x30,0x34,0x30,0x30,
0x00,0x00,0x00,0x23,0x00,0x01,0x00,0x04,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x08,
0x09,0x01,0x33,0x01,0x00,0x3d,0x01,0x76,0x00,0x00,0x00,0x01,0x00,0x04,0x74,0x6f,
0x5f,0x73,0x00,0x45,0x4e,0x44,0x00,0x00,0x00,0x00,0x08,
};
```

## Bringing it into Playdate

With the amalgam and some simple bytecode, had all the necessary pieces to boot a machine and execute the code. I set up an `initRuby()` function
and called it within the initializer of a project cloned from a simple Playdate C Example project:

```c
static void
initRuby(PlaydateAPI* pd)
{
    // Set global PlaydateAPI pointer
    g_pd = pd;
    // Initialize Ruby
   	ruby = mrb_open();

    // Load the precompiled ruby_source variable
    mrb_value resp = mrb_load_irep(ruby, ruby_source);
    // Catch exceptions on the Ruby VM
    if(ruby->exc){
        mrb_value m = mrb_funcall(ruby, mrb_obj_value(ruby->exc), "inspect", 0);
        g_pd->system->logToConsole("MRB Error: %s", mrb_str_to_cstr(ruby, m));
        ruby->exc = NULL;
    }

    // Log the result of ruby_source
    g_pd->system->logToConsole(mrb_str_to_cstr(resp));
}


// This is the default entry point for Playdate:
int eventHandler(PlaydateAPI* pd, PDSystemEvent event, uint32_t arg)
{
	(void)arg; // arg is currently only used for event = kEventKeyPressed

	if ( event == kEventInit )
	{
    	pd->system->logToConsole("Loaded up.");
	    initRuby(pd);
	}

	return 0;
}
```

This initial version immediately broke though, because I hadn't overridden the memory functions.

C code generally manages memory with calls to `malloc`. Ruby sets up some memory when booting up to put all the base classes in... but Playdate doesn't offer `malloc` directly so this immediately blows up. Playdate does allow access to memory, but provides a utility function in the SDK to interact with it instead of `malloc`: <https://sdk.play.date/3.1.1/Inside%20Playdate%20with%20C.html#_memory_allocation>

Luckily mruby allows overriding memory functions so I could use non-standard ones like Playdate provides. (This is documented in <https://github.com/mruby/mruby/blob/master/doc/guides/memory.md>). for playdate this means you need to create a `mrb_basic_alloc_func` in your code that pipes to the SDK utility that takes the same arguments. Windows C compiler doesn't seem to like overrides so I found I needed to comment out the amalgam provided one to get nmake to compile without yelling at me.

Note that pretty much all Playdate functionality requires a pointer to the Playdate SDK that is passed into the init function, so I store that as a global `g_pd` in my initRuby:

```c
// Define the memory management function
void* mrb_basic_alloc_func(void *p, size_t size) {
    return g_pd->system->realloc(p, size);
}
```

With that in place, I was able to get my first little bit of Ruby running on Playdate simulator. I then tried compiling it to the Playdate and found out I was missing a few amalgam compilation options - mostly to avoid STDIO support.

## Making Ruby able to do Playdate stuff

Logging to a console you can't even see on the actual device was a pretty boring example for me, so next came the fun part, making it so you can have Ruby actually driving the Playdate. MRuby allows you to define functions that are callable from the Ruby VM but escape out into C. Using this, I can build wrappers for the Playdate SDK and make those available from what look like Ruby classes - `Playdate::System.logToConsole('string')` becomes a call to the C function `g_pd->system->logToConsole(char* string)`

I started with the easy wrappers - logging to console and drawing fps on the screen. Every wrapper needs to be set on a class or module, and has the same basic signature - it takes the vm state and calling object as input args and returns a `mrb_value` for Ruby to pick up. Any arguments passed in are picked up separately with the `mrb_get_args()` method, which supports some simple type coercions back to C types. Here we need to be able to parse a string for console log, and X, Y coords to draw FPS.

```c bindings/system.c

static mrb_value
pd_system_logToConsole(mrb_state *mrb, mrb_value self)
{
    const char* a;
    mrb_get_args(mrb, "z", &a);

    g_pd->system->logToConsole(a);

    return mrb_nil_value();
}

static mrb_value
pd_system_drawFPS(mrb_state *mrb, mrb_value self)
{
    mrb_int a, b;
    mrb_get_args(mrb, "ii", &a, &b);

    g_pd->system->drawFPS(a, b);

    return mrb_nil_value();
}

void binding_pd_system(mrb_state *mrb) {
    g_pd->system->logToConsole("Preparing Playdate->System SDK functions...");

    struct RClass *pd = mrb_module_get(mrb, "Playdate");
    // Defines `Playdate::System` in Ruby
    struct RClass *system = mrb_define_module_under(mrb, pd, "System");
    // Makes `Playdate::System.logToConsole(arg)`
    mrb_define_module_function(mrb, system, "logToConsole", pd_system_logToConsole, MRB_ARGS_REQ(1));
    mrb_define_module_function(mrb, system, "drawFPS", pd_system_drawFPS, MRB_ARGS_REQ(2));
}
```

### Complex Ruby <-> C interop

Playdate provides a sprite handling library that I want to be able to use so I don't have to write my own collision detection. Sprites are allocated C objects that I need to wrap in a Ruby class, which makes them a bit more complicated than the functional wrappers like logToConsole. There are many parts to this:

1. A data type must be declared - this is a wrapper that tags the ruby object with a type

```c sprite.c
extern const mrb_data_type pd_sprite_type;

// sprite.c
// You must provide your plan to garbage collect value being wrapped
static void sprite_free(mrb_state *mrb, void *p){
    if (p) { g_pd->sprite->freeSprite((LCDSprite*)p); };
}
const mrb_data_type pd_sprite_type = { "Playdate::Sprite", sprite_free }; 
```

2. A boxing class in mruby is created and associated with the data type

```c
    struct RClass *sprite = mrb_define_class_under(mrb, pd, "Sprite", mrb->object_class);
    // mrb defined way to hint this class actually a C data type
    MRB_SET_INSTANCE_TT(sprite, MRB_TT_DATA);
```

3. When getting the pointer, associate it to the box class for use in Ruby. Associate
   the ruby object pointer as necessary back to the raw pointer because there is no
   easy way to take a C pointer and get back the _same_ Ruby object (you'd end
   creating a new instance boxing the same pointer) 
   
   Sprite class has a way to do this with _"userdata"_, otherwise a C wrapper of some kind might
   be needed - box the boxed value :D?

```c
static mrb_value pd_sprite_initialize(mrb_state *mrb, mrb_value self){
    LCDSprite *s = g_pd->sprite->newSprite();
    // Tie the Ruby Sprite data to the LCDSprite pointer
    mrb_data_init(self, s, &pd_sprite_type);
    // RData is void* and can be set in userdata, mrb_value isn't necessarily that type. Extract the raw pointer
    // and associate it with the LCDSprite
    g_pd->sprite->setUserdata(s, mrb_ptr(self));

    return self;
}
```

4. Define methods as needed to interact with the underlying object - you must
   unbox it with the data type defined earlier 
      
   This can be easier if you set up a helper to do the unboxing.

```c sprite.c
static mrb_value pd_sprite_moveTo(mrb_state *mrb, mrb_value self){
    mrb_int x, y;
    mrb_get_args(mrb, "ii", &x, &y);
    // Use helper to unbox Sprite in ruby to LCDSprite pointer in C
    // pd_sprite_get is defined as `(LCDSprite*)mrb_data_get_ptr(mrb, self, &pd_sprite_type)`;
    LCDSprite* s = pd_sprite_get(mrb, self); 
    g_pd->sprite->moveTo(s, (float)x, (float)y);
    return self;
}
```

It's a bit of work to get all the pieces in place, but with these functions built out and similar ones for Bitmap images, you can have ruby that looks like this to have a sprite that jumps around

```ruby game.rb

@sprite = Playdate::Sprite.new
@sprite.set_image(Playdate::Graphics.loadBitmap('images/test'))
@sprite.move_to(1, 1)

def game_update
  @sprite.move_to(rand(100), rand(100))
  Playdate::Sprite.drawSprites()
end
```

The sprites worked for a minute... then the images disappeared. Turned out they were being garbage collected - I needed to tie together the C picture and the Ruby picture with the Ruby image being set as an instance variable on the Ruby sprite to match the tie together between the C sprite and C image.

```c sprite.c
static mrb_value pd_sprite_setImage(mrb_state *mrb, mrb_value self){
    mrb_value bitmap;
    mrb_get_args(mrb, "o", &bitmap);
    // Use helper to unbox Sprite in ruby to LCDSprite pointer in C
    LCDSprite* s = pd_sprite_get(mrb, self);
    // Save pointer to bitmap on the sprite boxed object to avoid collection
    mrb_iv_set(mrb, self, mrb_intern_lit(mrb, "@image"), bitmap);

    LCDBitmap* b = pd_bitmap_get(mrb, bitmap);

    // TODO: allow flippage
    g_pd->sprite->setImage(s, b, kBitmapUnflipped);
    return self;
}
```

### Tying it together and scaling

With all those bits worked out and running on an actual device, I could start pushing toward something that looks more like a game. I expanded how I handle Ruby code with two phases of Ruby loading. First a standard library I'm calling Pyrite that gets compiled directly into the C code as bytecode so I can provide standard conveniences around the SDK. After that loads, we use the Playdate SDK to load actual game bytecode from the game folder dynamically and execute it - in theory this lets us compile the Ruby parts of the game without the rest of the C compiling part.

```c main.c
int eventHandler(PlaydateAPI* pd, PDSystemEvent event, uint32_t arg)
{
	(void)arg; // arg is currently only used for event = kEventKeyPressed

	if ( event == kEventInit )
	{
    pd->system->logToConsole("Loaded up.");
	  ruby = initRuby(pd);

    //
		mrb_load_irep(ruby, ruby_source);
    pyrite = mrb_module_get(ruby, "Pyrite");
		ruby_report_any_exception(ruby);
		pd->system->logToConsole("Pyrite libraries loaded.");

		load_mrb_file(ruby, "cartridge/main.mrb");
		pd->system->logToConsole("Ruby cartridge loaded.");

		const char* err;
		font = pd->graphics->loadFont(fontpath, &err);

		if ( font == NULL )
			pd->system->error("%s:%i Couldn't load font %s: %s", __FILE__, __LINE__, fontpath, err);

		pd->graphics->setFont(font);

		// Note: If you set an update callback in the kEventInit handler, the system assumes the game is pure C and doesn't run any Lua code in the game
		pd->system->setUpdateCallback(update, pd);
	}

	return 0;
}

static int update(void* userdata)
{
    PlaydateAPI* pd = userdata;
    float dt = pd->system->getElapsedTime();

    // Ensure intermediate mrb_values created in Ruby->C calls are thrown away by setting a checkpoint
    int snapshot = mrb_gc_arena_save(ruby);

    mrb_funcall_id(ruby, mrb_obj_value(pyrite), mrb_intern_lit(ruby, "game_update"), 1, mrb_float_value(ruby, dt));
    if(ruby->exc){
        mrb_value m = mrb_funcall(ruby, mrb_obj_value(ruby->exc), "inspect", 0);
        pd->system->logToConsole("Cartridge Error: %s", mrb_str_to_cstr(ruby, m));
        ruby->exc = NULL;
    }
    
    // reset to the checkpoint to collect any intermediate mrb_values
    mrb_gc_arena_restore(ruby, snapshot);
    pd->system->resetElapsedTime();
    
    return 1;
}
```

This relies on a global Pyrite module defining our game_update, as well as the eventual game cartridge calling `Pyrite.load_cartridge()`

```ruby pyrite.rb
module Pyrite
  def self.load_cartridge(klass)
    @current = klass.new
    @current.prepare
  end

  def self.current_cartridge
    raise "No cartridge loaded" unless @current
  end

  def self.game_update(dt = 0)
    @current.delta_time = dt
    # simplified...
    @current.update
    @current.draw
  end
end
```

Then the cartridge defines a prepare, draw and update method:

```ruby cartridge/game.rb

class HelloMruby < Pyrite::Cartridge
  def initialize
    @debug = true
  end
  def prepare
  end
  
  def update
  end
  
  def draw
  end
end

Pyrite.load_cartridge(HelloMruby)
```

With all those pieces together, I can start building an actual ruby game framework, adding SDK wrappers as needed. I'm building it as a new open source project that can hopefully become my new go-to when I want to do a Playdate game jam! 

<https://github.com/therabidbanana/playdate-mruby>
